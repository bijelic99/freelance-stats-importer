package com.freelanceStats.components.jobArchiverFactory

import akka.stream.scaladsl.Flow
import com.freelanceStats.commons.models.{RawJob, UnsavedRawJob}

trait JobArchiverFactory {
  def create: Flow[UnsavedRawJob, RawJob, _]
}
