package com.freelanceStats.components.dataSourceFactory

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import com.freelanceStats.commons.models.UnsavedRawJob
import com.freelanceStats.configurations.sources.SourceConfiguration
import com.freelanceStats.models.page.Page
import com.freelanceStats.models.pageMetadata.ProgressMetadata
import com.freelanceStats.s3Client.S3Client
import play.api.libs.json.{Json, Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}

trait DataSourceFactory[Metadata <: ProgressMetadata] {

  implicit val executionContext: ExecutionContext

  implicit val materializer: Materializer

  def s3Client: S3Client

  def configuration: SourceConfiguration

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

  def saveProgressMetadata()(implicit
      writes: Writes[Metadata]
  ): Sink[Metadata, Future[Done]] = Sink.foreachAsync(1) { progressMetadata =>
    val source =
      Source.single(ByteString(Json.toBytes(Json.toJson(progressMetadata))))
    s3Client
      .put(configuration.lastPageMetadataFile, source)
      .map(_ => ())
  }

  def create: Source[UnsavedRawJob, _]
}
