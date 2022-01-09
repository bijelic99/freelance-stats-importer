package com.freelanceStats.components.dataSourceFactory

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.freelanceStats.components.S3Client
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration
import com.freelanceStats.models.page.{FreelancerPage, Page}
import com.freelanceStats.models.pageMetadata.FreelancerPageMetadata
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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
          .unfold(
            lstPageMetadata.createNext(configuration.maxFetchOffset)
          ) { metadata =>
            Some(
              (
                metadata.createNext(configuration.maxFetchOffset),
                metadata
              )
            )
          }
          .throttle(1, configuration.frequency)
      )
      .via(fetchPage)
      .wireTap(
        Sink
          .foreachAsync[FreelancerPage](1)(page =>
            saveLastPageMetadata(page.metadata)
          )
      )

  private lazy val pool = Http().superPool[FreelancerPageMetadata]()

  lazy val fetchPage: Flow[FreelancerPageMetadata, FreelancerPage, _] =
    Flow[FreelancerPageMetadata]
      .map { metadata =>
        val fetchFromSeconds =
          TimeUnit.MILLISECONDS.toSeconds(metadata.fetchFrom.getMillis)
        val fetchToSeconds =
          TimeUnit.MILLISECONDS.toSeconds(metadata.fetchTo.getMillis)
        HttpRequest(
          method = HttpMethods.GET,
          uri =
            s"${configuration.url}/api/projects/0.1/projects/active?from_time=$fetchFromSeconds&to_time=$fetchToSeconds"
        ) -> metadata
      }
      .via(pool)
      .map {
        case (Success(response), metadata) if response.status.isSuccess() =>
          FreelancerPage(
            metadata,
            response.entity.dataBytes
          )
        case (Success(response), metadata) =>
          val message =
            s"Received response with code ${response.status} for '$metadata'"
          log.error(message)
          throw new Exception(message)
        case (Failure(throwable), metadata) =>
          val message = s"Failed to receive response for '$metadata'"
          log.error(message, throwable)
          throw new Exception(message, throwable)
      }
}
