package com.freelanceStats.components.jobArchiverFactory

import akka.event.{Logging, LoggingAdapter}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.freelanceStats.commons.models.{RawJob, UnsavedRawJob}

trait JobArchiverFactory {

  def materializer: Materializer

  implicit val log: LoggingAdapter = Logging(
    materializer.system,
    "com.freelanceStats.components.jobArchiverFactory.JobArchiverFactory"
  )

  def create: Flow[UnsavedRawJob, RawJob, _]
}
