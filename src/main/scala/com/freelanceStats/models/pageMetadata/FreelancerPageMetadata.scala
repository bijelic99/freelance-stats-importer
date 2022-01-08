package com.freelanceStats.models.pageMetadata

import org.joda.time.{DateTime, ReadableDuration}
import play.api.libs.json.{Format, Json}

import com.github.nscala_time.time.Imports._
import scala.util.chaining._

case class FreelancerPageMetadata(
    fetchFrom: DateTime,
    fetchTo: DateTime = DateTime.now()
) extends PageMetadata

object FreelancerPageMetadata {
  import com.freelanceStats.commons.implicits.playJson.DateTimeFormat._

  implicit val format: Format[FreelancerPageMetadata] =
    Json.format[FreelancerPageMetadata]

  implicit class FreelancerPageMetadataOps(
      freelancerPageMetadata: FreelancerPageMetadata
  ) {
    def createNext(maxOffset: ReadableDuration): FreelancerPageMetadata = {
      val fetchTo =
        (freelancerPageMetadata.fetchTo + maxOffset)
          .pipe {
            case ft if ft > DateTime.now() =>
              DateTime.now()
            case ft =>
              ft
          }
      FreelancerPageMetadata(
        fetchFrom = freelancerPageMetadata.fetchTo,
        fetchTo = fetchTo
      )
    }
  }
}
