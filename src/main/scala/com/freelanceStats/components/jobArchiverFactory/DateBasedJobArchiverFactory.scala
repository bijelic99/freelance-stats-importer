package com.freelanceStats.components.jobArchiverFactory

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import com.freelanceStats.commons.models.{RawJob, UnsavedRawJob}
import com.freelanceStats.components.S3Client
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.s3Client.models.FileReference
import org.apache.tika.config.TikaConfig
import org.slf4j.LoggerFactory

import javax.inject.Inject

class DateBasedJobArchiverFactory @Inject() (
    s3Client: S3Client,
    applicationConfiguration: ApplicationConfiguration
)(implicit
    materializer: Materializer
) extends JobArchiverFactory {

  private val log = LoggerFactory.getLogger(getClass)

  private val mimeTypes = TikaConfig.getDefaultConfig.getMimeRepository

  override def create: Flow[UnsavedRawJob, RawJob, _] =
    Flow[UnsavedRawJob]
      .flatMapConcat {
        case UnsavedRawJob(
              sourceId,
              source,
              created,
              modified,
              contentType,
              contentSize,
              data
            ) =>
          val createdDateStr = created.toString("dd-MM-yyyy")
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
            .map(RawJob(sourceId, source, created, modified, _))
      }
}
