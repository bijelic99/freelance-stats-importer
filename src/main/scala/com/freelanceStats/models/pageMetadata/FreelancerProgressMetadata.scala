package com.freelanceStats.models.pageMetadata

import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import play.api.libs.json.{Format, Json}

import scala.util.chaining._

case class FreelancerProgressMetadata(
    fetchFrom: DateTime,
    fetchTo: DateTime = DateTime.now(),
    offset: Int = 0
) extends ProgressMetadata

object FreelancerProgressMetadata {
  import com.freelanceStats.commons.implicits.playJson.DateTimeFormat._

  implicit val format: Format[FreelancerProgressMetadata] =
    Json.format[FreelancerProgressMetadata]

  implicit class FreelancerProgressMetadataOps(
      freelancerProgressMetadata: FreelancerProgressMetadata
  ) {

    def createNextProgressMetadata(
        maxOffset: Period
    ): FreelancerProgressMetadata = {
      val fetchTo =
        (freelancerProgressMetadata.fetchTo + maxOffset)
          .pipe {
            case ft if ft > DateTime.now() =>
              DateTime.now()
            case ft =>
              ft
          }
      FreelancerProgressMetadata(
        fetchFrom = freelancerProgressMetadata.fetchTo,
        fetchTo = fetchTo
      )
    }

    def incrementResultOffset(resultLimit: Int): FreelancerProgressMetadata =
      freelancerProgressMetadata
        .copy(
          offset = freelancerProgressMetadata.offset + resultLimit
        )

  }
}
