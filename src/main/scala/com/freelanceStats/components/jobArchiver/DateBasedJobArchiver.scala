package com.freelanceStats.components.jobArchiver

import akka.event.{Logging, LoggingAdapter}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import com.freelanceStats.commons.models.{RawJob, UnsavedRawJob}
import com.freelanceStats.components.S3Client
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.s3Client.models.FileReference
import org.apache.tika.config.TikaConfig
import org.joda.time.DateTime

import javax.inject.Inject

class DateBasedJobArchiver @Inject() (
    s3Client: S3Client,
    applicationConfiguration: ApplicationConfiguration
)(override implicit val materializer: Materializer)
    extends JobArchiver {

  private val mimeTypes = TikaConfig.getDefaultConfig.getMimeRepository

  val name: String = "job-archiver"

  override implicit val log: LoggingAdapter =
    Logging(materializer.system, getClass)

  override def apply(): Flow[UnsavedRawJob, RawJob, _] =
    Flow[UnsavedRawJob]
      .log(
        name,
        { case UnsavedRawJob(sourceId, _, _, _, _) =>
          s"Archiving job with $sourceId"
        }
      )
      .flatMapConcat {
        case UnsavedRawJob(
              sourceId,
              source,
              contentType,
              contentSize,
              data
            ) =>
          val createdDateStr = DateTime.now().toString("dd-MM-yyyy")
          val futureFileReference =
            FileReference(
              bucket = applicationConfiguration.bucket,
              key =
                s"${applicationConfiguration.source}/$createdDateStr/$sourceId${mimeTypes.forName(contentType).getExtension}",
              contentType = Some(contentType),
              size = Some(contentSize)
            )
          Source
            .single(futureFileReference -> data)
            .via(s3Client.putFlow)
            .map(RawJob(sourceId, source, _))
            .log(
              name,
              { case RawJob(sourceId, _, _) =>
                s"Archived job with $sourceId successfully"
              }
            )
      }
}
