package com.freelanceStats.components.jobArchiverFactory

import akka.http.scaladsl.model.ContentType
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import com.freelanceStats.commons.models.{RawJob, UnsavedRawJob}
import com.freelanceStats.components.S3Client
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.s3Client.models.FileReference
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import javax.inject.Inject

class DateBasedJobArchiverFactory @Inject() (
    s3Client: S3Client,
    applicationConfiguration: ApplicationConfiguration
)(implicit
    materializer: Materializer
) extends JobArchiverFactory {

  private val log = LoggerFactory.getLogger(getClass)

  override def create: Flow[UnsavedRawJob, RawJob, _] =
    Flow[UnsavedRawJob]
      .flatMapConcat {
        case UnsavedRawJob(id, sourceId, source, created, modified, data) =>
          val currentDate = DateTime.now().toDate.toString
          val futureFileReference =
            FileReference(
              bucket = applicationConfiguration.bucket,
              key = s"${applicationConfiguration.source}/$currentDate",
              contentType = Some("application/json")
            )
          Source
            .single(futureFileReference -> data)
            .via(s3Client.putFlow)
            .map(RawJob(id, sourceId, source, created, modified, _))
      }
}
