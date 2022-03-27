package com.freelanceStats.components.dataSource

import akka.stream.scaladsl.Source
import com.freelanceStats.commons.models.UnsavedRawJob

trait DataSource {
  def apply(): Source[UnsavedRawJob, _]
}
