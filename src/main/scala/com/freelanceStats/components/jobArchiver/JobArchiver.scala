package com.freelanceStats.components.jobArchiver

import akka.event.{Logging, LoggingAdapter}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.freelanceStats.commons.models.{RawJob, UnsavedRawJob}

trait JobArchiver {

  def materializer: Materializer

  implicit val log: LoggingAdapter = Logging(
    materializer.system,
    "com.freelanceStats.components.jobArchiver.JobArchiver"
  )

  def apply(): Flow[UnsavedRawJob, RawJob, _]
}
