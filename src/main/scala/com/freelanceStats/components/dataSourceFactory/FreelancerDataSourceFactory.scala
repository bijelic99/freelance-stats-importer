package com.freelanceStats.components.dataSourceFactory

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.freelanceStats.components.S3Client
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration
import com.freelanceStats.models.page.{FreelancerPage, Page}
import com.freelanceStats.models.pageMetadata.FreelancerPageMetadata
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit
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

  private val log = LoggerFactory.getLogger(getClass)

  protected override def defaultPageMetadata: FreelancerPageMetadata =
    FreelancerPageMetadata.apply(
      fetchFrom = DateTime.now(),
      fetchTo = DateTime.now()
    )

  override def create: Source[Page[FreelancerPageMetadata], _] =
    Source
      .future(lastPageMetadata())
      .flatMapConcat(lstPageMetadata =>
        Source
          .unfoldAsync(
            lstPageMetadata.createNext(configuration.maxFetchOffset)
          ) { metadata =>
            saveLastPageMetadata(metadata)
              .flatMap(_ => fetchPage(metadata))
              .map(
                _.map(metadata.createNext(configuration.maxFetchOffset) -> _)
              )
          }
          .throttle(1, configuration.frequency)
      )

  private lazy val pool = Http().superPool[FreelancerPageMetadata]()

  def fetchPage(
      pageMetadata: FreelancerPageMetadata
  ): Future[Option[FreelancerPage]] =
    Source
      .single(pageMetadata)
      .map { case FreelancerPageMetadata(fetchFrom, fetchTo) =>
        val fetchFromSeconds =
          TimeUnit.MILLISECONDS.toSeconds(fetchFrom.getMillis)
        val fetchToSeconds = TimeUnit.MILLISECONDS.toSeconds(fetchTo.getMillis)
        HttpRequest(
          method = HttpMethods.GET,
          uri =
            s"${configuration.url}/api/projects/0.1/projects/active?from_time=$fetchFromSeconds&to_time=$fetchToSeconds"
        ) -> pageMetadata
      }
      .via(pool)
      .map {
        case (Success(response), metadata) if response.status.isSuccess() =>
          Some(
            FreelancerPage(
              metadata,
              response.entity.dataBytes
            )
          )
        case _ =>
          None
      }
      .runWith(Sink.head)
}
