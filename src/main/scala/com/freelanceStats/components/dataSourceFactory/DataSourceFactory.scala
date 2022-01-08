package com.freelanceStats.components.dataSourceFactory

import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import com.freelanceStats.configurations.sources.SourceConfiguration
import com.freelanceStats.models.page.Page
import com.freelanceStats.models.pageMetadata.PageMetadata
import com.freelanceStats.s3Client.S3Client
import play.api.libs.json.{Json, Reads}

import scala.concurrent.{ExecutionContext, Future}

trait DataSourceFactory[Metadata <: PageMetadata] {

  implicit val executionContext: ExecutionContext

  implicit val materializer: Materializer

  def s3Client: S3Client

  def configuration: SourceConfiguration

  protected def defaultPageMetadata: Metadata

  protected def lastPageMetadata()(implicit
      reads: Reads[Metadata]
  ): Future[Metadata] =
    s3Client
      .get(configuration.lastPageMetadataFile)
      .map {
        case Some((_, source)) =>
          Json
            .parse(source.runWith(StreamConverters.asInputStream()))
            .as[Metadata]
        case None =>
          defaultPageMetadata
      }

  def create: Source[Page[Metadata], _]
}
