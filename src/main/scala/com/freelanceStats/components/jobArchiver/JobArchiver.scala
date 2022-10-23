package com.freelanceStats.components.jobArchiver

import akka.stream.scaladsl.Flow
import com.freelanceStats.commons.models.{RawJob, UnsavedRawJob}

trait JobArchiver {
  def apply(): Flow[UnsavedRawJob, RawJob, _]
}
