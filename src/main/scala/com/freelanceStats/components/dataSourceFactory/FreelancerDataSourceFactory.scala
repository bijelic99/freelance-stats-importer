package com.freelanceStats.components.dataSourceFactory

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.{DelayOverflowStrategy, Materializer}
import akka.stream.scaladsl.{Sink, Source}
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration
import com.freelanceStats.models.page.{FreelancerPage, Page}
import com.freelanceStats.models.pageMetadata.FreelancerPageMetadata
import com.freelanceStats.s3Client.S3Client
import org.joda.time.DateTime

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class FreelancerDataSourceFactory @Inject() (
    override val s3Client: S3Client,
    override val configuration: FreelancerSourceConfiguration
)(
    implicit val actorSystem: ActorSystem,
    override implicit val executionContext: ExecutionContext,
    override implicit val materializer: Materializer
) extends DataSourceFactory[FreelancerPageMetadata] {
  import FreelancerPageMetadata._

  protected override def defaultPageMetadata: FreelancerPageMetadata =
    FreelancerPageMetadata.apply(
      fetchFrom = DateTime.now(),
      fetchTo = DateTime.now()
    )

  override def create: Source[Page[FreelancerPageMetadata], _] =
    Source
      .future(lastPageMetadata())
      .map(_.createNext(configuration.maxFetchOffset))
      .flatMapConcat(lstPageMetadata =>
        Source
          .unfoldAsync(lstPageMetadata)(fetchPage)
          .delay(configuration.frequency, DelayOverflowStrategy.backpressure)
      )

  private lazy val pool = Http().superPool[FreelancerPageMetadata]()

  def fetchPage(
      pageMetadata: FreelancerPageMetadata
  ): Future[Option[(FreelancerPageMetadata, FreelancerPage)]] =
    Source
      .single(pageMetadata)
      .map { case FreelancerPageMetadata(fetchFrom, fetchTo) =>
        HttpRequest(
          method = HttpMethods.GET,
          uri =
            s"${configuration.url}/api/projects/0.1/projects/active?from_time=${fetchFrom.getMillis}&to_time=${fetchTo.getMillis}"
        ) -> pageMetadata
      }
      .via(pool)
      .map {
        case (Success(response), metadata) if response.status.isSuccess() =>
          Some(
            (
              pageMetadata.createNext(configuration.maxFetchOffset),
              FreelancerPage(
                metadata,
                response.entity.dataBytes
              )
            )
          )
        case _ =>
          None
      }
      .runWith(Sink.head)
}
