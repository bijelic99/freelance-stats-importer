package com.freelanceStats.components.dataSource.freelancer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.{Attributes, Materializer}
import akka.stream.scaladsl.{Flow, Sink, StreamConverters}
import com.freelanceStats.commons.models.UnsavedRawJob
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration
import org.apache.commons.lang3.exception.ExceptionUtils

import java.io.ByteArrayInputStream
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

case class JobFetchFlow(
    configuration: FreelancerSourceConfiguration,
    applicationConfiguration: ApplicationConfiguration
)(
    implicit val actorSystem: ActorSystem,
    implicit val executionContext: ExecutionContext,
    implicit val materializer: Materializer
) {

  implicit val log: LoggingAdapter =
    Logging(materializer.system, getClass)

  private lazy val jobFetchConnectionPool =
    Http().superPool[String]()

  val jobFetchParams: String =
    Seq(
      "compact=true",
      "full_description=true",
      "job_details=true",
      "qualification_details=true",
      "user_details=true",
      "location_details=true",
      "user_country_details=true",
      "user_location_details=true"
    ).mkString("?", "&", "")

  private def jobFetchErrorSink =
    Flow[(Try[UnsavedRawJob], String)]
      .collect { case (Failure(exception), id) =>
        ExceptionUtils.getStackTrace(
          new Exception(s"Error while processing '$id'", exception)
        )
      }
      .log("job-fetch-error")
      .withAttributes(Attributes.logLevels(onElement = Logging.ErrorLevel))
      .to(Sink.ignore)

  def apply(): Flow[String, UnsavedRawJob, NotUsed] =
    Flow[String]
      .map { id =>
        HttpRequest(
          method = HttpMethods.GET,
          uri =
            s"${configuration.url}/api/projects/0.1/projects/$id$jobFetchParams"
        ) -> id
      }
      .via(jobFetchConnectionPool)
      .map {
        case (Success(response), id) if response.status.isSuccess() =>
          val byteArray = response.entity.dataBytes
            .runWith(StreamConverters.asInputStream())
            .readAllBytes()
          Success(
            UnsavedRawJob(
              sourceId = id,
              source = applicationConfiguration.source,
              data = StreamConverters
                .fromInputStream(() => new ByteArrayInputStream(byteArray)),
              contentType = response.entity.contentType.toString(),
              contentSize =
                response.entity.contentLengthOption.getOrElse(byteArray.length)
            )
          ) -> id
        case (Success(response), id) =>
          Failure(
            new Exception(
              s"Api returned non 200 status code: '${response.status}'"
            )
          ) -> id
        case (Failure(throwable), id) =>
          Failure(throwable) -> id
      }
      .divertTo(jobFetchErrorSink, _._1.isFailure)
      .collect { case (Success(job), _) =>
        job
      }
}
