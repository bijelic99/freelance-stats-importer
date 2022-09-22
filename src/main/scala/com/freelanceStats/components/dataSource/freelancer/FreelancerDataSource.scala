package com.freelanceStats.components.dataSource.freelancer

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Keep, Sink, Source, StreamConverters}
import akka.stream.typed.scaladsl.ActorSource
import com.freelanceStats.actors.PeriodProvider
import com.freelanceStats.commons.models.UnsavedRawJob
import com.freelanceStats.components.dataSource.DataSource
import com.freelanceStats.components.lastImportedElementDateTimeProvider.LastImportedElementDateTimeProvider
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration
import play.api.libs.json.{JsObject, Json}

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class FreelancerDataSource @Inject() (
    val configuration: FreelancerSourceConfiguration,
    applicationConfiguration: ApplicationConfiguration,
    lastImportedElementDateTimeProvider: LastImportedElementDateTimeProvider
)(
    implicit val actorSystem: ActorSystem,
    implicit val executionContext: ExecutionContext,
    implicit val materializer: Materializer
) extends DataSource {

  implicit val log: LoggingAdapter =
    Logging(materializer.system, getClass)

  private lazy val apiCursorConnectionPool =
    Http().superPool[PeriodProvider.Period]()

  private def jobSeekParams(period: PeriodProvider.Period) = Seq(
    "compact=true",
    s"from_time=${period.from.getMillis / 1000}",
    s"to_time=${period.to.getMillis / 1000}"
  ).mkString("?", "&", "")

  private def idSource = Source
    .lazySingle(() =>
      actorSystem
        .spawn(
          PeriodProvider(
            PeriodProvider.Props(
              configuration.sourceThrottlePer,
              lastImportedElementDateTimeProvider,
              applicationConfiguration.source
            )
          ),
          "period-provider"
        )
        .toClassic
    )
    .flatMapConcat { periodProviderActor =>
      ActorSource
        .actorRefWithBackpressure[
          PeriodProvider.StreamCommand,
          PeriodProvider.Ack.type
        ](
          ackTo = periodProviderActor,
          ackMessage = PeriodProvider.Ack,
          completionMatcher = PartialFunction.empty,
          failureMatcher = { case PeriodProvider.UnexpectedError(t) => t }
        )
        .collectType[PeriodProvider.Period]
        .alsoToMat(Sink.ignore)(Keep.both)
        .mapMaterializedValue { case (sourceActor, future) =>
          periodProviderActor ! PeriodProvider.Initialize(sourceActor)
          future.andThen(_ => actorSystem.stop(periodProviderActor))
          sourceActor -> periodProviderActor
        }
        .map { period =>
          HttpRequest(
            method = HttpMethods.GET,
            uri =
              s"${configuration.url}/api/projects/0.1/projects/active/${jobSeekParams(period)}"
          ) -> period
        }
        .via(apiCursorConnectionPool)
        .mapConcat {
          case (Success(response), _) if response.status.isSuccess() =>
            (Json.parse(
              response.entity.dataBytes
                .runWith(StreamConverters.asInputStream())
            ) \ "result" \ "projects")
              .as[Seq[JsObject]]
              .map(_.\("id").as[Long].toString)
          case (Success(response), _) =>
            throw new Exception(
              s"Api returned non 200 status code: '${response.status}'"
            )
          case (Failure(throwable), _) =>
            throw throwable
        }
    }

  override def apply(): Source[UnsavedRawJob, _] =
    idSource
      .buffer(10000, OverflowStrategy.backpressure)
      .via(JobFetchFlow(configuration, applicationConfiguration).apply())
}

object FreelancerDataSource {
  case class ParsedRssFeed(
      lastBatch: Seq[String] = Nil,
      difference: Seq[String] = Nil
  )
}
