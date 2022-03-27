package com.freelanceStats.components.dataSource
import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.scaladsl.{Flow, Sink, Source, StreamConverters}
import akka.stream.{Attributes, Materializer}
import com.freelanceStats.commons.models.UnsavedRawJob
import com.freelanceStats.components.dataSource.FreelancerDataSource.ParsedRssFeed
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration

import java.io.ByteArrayInputStream
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}

class FreelancerDataSource @Inject() (
    val configuration: FreelancerSourceConfiguration,
    applicationConfiguration: ApplicationConfiguration
)(
    implicit val actorSystem: ActorSystem,
    implicit val executionContext: ExecutionContext,
    implicit val materializer: Materializer
) extends DataSource {

  implicit val log: LoggingAdapter =
    Logging(materializer.system, getClass)

  private lazy val rssFeedConnectionPool =
    Http().superPool[NotUsed]()

  private def rssFeedFetchErrorSink: Sink[Try[Elem], NotUsed] =
    Flow[Try[Elem]]
      .collect { case Failure(exception) =>
        exception
      }
      .log("rss-feed-error")
      .withAttributes(Attributes.logLevels(onElement = Logging.ErrorLevel))
      .to(Sink.ignore)

  private def rssFeedSource: Source[Elem, NotUsed] =
    Source
      .repeat(NotUsed)
      .map(
        HttpRequest(
          method = HttpMethods.GET,
          uri = s"${configuration.url}/rss.xml"
        ) -> _
      )
      .via(rssFeedConnectionPool)
      .map {
        case (Success(response), _) if response.status.isSuccess() =>
          Success(
            XML.load(
              response.entity.dataBytes
                .runWith(StreamConverters.asInputStream())
            )
          )
        case (Success(response), _) =>
          Failure(
            new Exception(
              s"Rss feed returned non 200 status code: '${response.status}'"
            )
          )
        case (Failure(exception), _) =>
          Failure(exception)
      }
      .divertTo(
        rssFeedFetchErrorSink,
        _.isFailure
      )
      .collect { case Success(elem) =>
        elem
      }

  private lazy val idExtractRegex = """^.+_(\d+)$""".r //"""_(\d+)$""".r

  private def idExtractFlow: Flow[Elem, Seq[String], NotUsed] =
    Flow[Elem]
      .map { feed =>
        (feed \\ "item")
          .map(_ \ "guid")
          .map(_.text)
          .collect { case idExtractRegex(id) =>
            id
          }
      }

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
        new Exception(s"Error while processing '$id'", exception)
      }
      .log("job-fetch-error")
      .withAttributes(Attributes.logLevels(onElement = Logging.ErrorLevel))
      .to(Sink.ignore)

  private def jobFetchFlow: Flow[String, UnsavedRawJob, NotUsed] =
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

  override def apply(): Source[UnsavedRawJob, _] =
    rssFeedSource
      .via(idExtractFlow)
      .scan(ParsedRssFeed()) {
        case (ParsedRssFeed(lastBatch, _, _), currentBatch) =>
          val diff = currentBatch.filterNot(lastBatch.contains)
          ParsedRssFeed(currentBatch, diff, currentBatch.size - diff.size)
      }
      .throttle(
        configuration.sourceThrottleMaxCost,
        configuration.sourceThrottlePer,
        _.numberOfDuplicates
      )
      .flatMapConcat(feed => Source(feed.difference))
      .via(jobFetchFlow)
}

object FreelancerDataSource {
  case class ParsedRssFeed(
      lastBatch: Seq[String] = Nil,
      difference: Seq[String] = Nil,
      numberOfDuplicates: Int = 0
  )
}
