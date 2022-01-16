package com.freelanceStats.components.jobCreatorFactory

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source, StreamConverters}
import com.freelanceStats.commons.models.UnsavedRawJob
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.models.page.{FreelancerPage, Page}
import com.freelanceStats.models.pageMetadata.FreelancerPageMetadata
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, Json}

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.util.chaining._

class FreelancerJobCreatorFactory @Inject() (
    applicationConfiguration: ApplicationConfiguration
)(implicit
    val materializer: Materializer
) extends JobCreatorFactory[FreelancerPageMetadata] {

  private val log = LoggerFactory.getLogger(getClass)

  override def create: Flow[Page[FreelancerPageMetadata], UnsavedRawJob, _] =
    Flow[Page[FreelancerPageMetadata]]
      .flatMapConcat {
        case FreelancerPage(metadata, contents) =>
          log.debug(s"Parsing page with the following metadata: '$metadata''")
          val page =
            Json.parse(contents.runWith(StreamConverters.asInputStream()))
          Source(
            (page \ "result" \ "projects")
              .asOpt[Seq[JsObject]]
              .getOrElse(Nil)
              .map { job =>
                val jobByteArray = Json.toBytes(job)
                UnsavedRawJob(
                  sourceId = (job \ "id").as[Long].toString,
                  source = applicationConfiguration.source,
                  created = (job \ "time_submitted")
                    .as[Long]
                    .pipe(s => new DateTime(TimeUnit.SECONDS.toMillis(s))),
                  modified = (job \ "time_updated")
                    .as[Long]
                    .pipe(s => new DateTime(TimeUnit.SECONDS.toMillis(s))),
                  data = StreamConverters
                    .fromInputStream(() =>
                      new ByteArrayInputStream(Json.toBytes(job))
                    ),
                  contentType = "application/json",
                  contentSize = jobByteArray.length
                )
              }
          )
        case page =>
          log.warn(s"Received wrong type of page: '$page''")
          Source.empty
      }
}
