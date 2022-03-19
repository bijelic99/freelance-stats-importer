package com.freelanceStats.components.dataSource

import akka.Done
import akka.event.{Logging, LoggingAdapter}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import akka.util.ByteString
import com.freelanceStats.commons.models.UnsavedRawJob
import com.freelanceStats.configurations.sources.SourceConfiguration
import com.freelanceStats.models.pageMetadata.ProgressMetadata
import com.freelanceStats.s3Client.S3Client
import play.api.libs.json.{Json, Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}

trait DataSource[Metadata <: ProgressMetadata] {

  implicit val executionContext: ExecutionContext

  implicit val materializer: Materializer

  def s3Client: S3Client

  def configuration: SourceConfiguration

  val name: String = "data-source"

  implicit val log: LoggingAdapter =
    Logging(
      materializer.system,
      "com.freelanceStats.components.dataSource.DataSource"
    )

  protected def defaultPageMetadata: Metadata

  protected def lastProgressMetadata()(implicit
      reads: Reads[Metadata]
  ): Source[Option[Metadata], _] = Source.future {
    s3Client
      .get(configuration.lastPageMetadataFile)
      .map(
        _.map { case (_, source) =>
          Json
            .parse(source.runWith(StreamConverters.asInputStream()))
            .as[Metadata]
        }
      )
  }

  protected def firstProgressMetadata()(implicit
      reads: Reads[Metadata]
  ): Source[Metadata, _] =
    lastProgressMetadata()
      .map {
        case Some(lastPageMetadata) =>
          lastPageMetadata
        case None =>
          defaultPageMetadata
      }
      .log(
        name,
        { metadata: Metadata =>
          s"Using '$metadata' as firstProgressMetadata"
        }
      )

  def saveProgressMetadata()(implicit
      writes: Writes[Metadata]
  ): Sink[Metadata, Future[Done]] =
    Flow[Metadata]
      .log(
        name,
        { metadata =>
          s"Saving '$metadata'"
        }
      )
      .toMat {
        Sink.foreachAsync(1) { progressMetadata =>
          val source =
            Source.single(
              ByteString(Json.toBytes(Json.toJson(progressMetadata)))
            )
          s3Client
            .put(configuration.lastPageMetadataFile, source)
            .map(_ => ())
        }
      }(Keep.right)

  def apply(): Source[UnsavedRawJob, _]
}
