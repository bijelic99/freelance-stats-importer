package com.freelanceStats.components.jobCreatorFactory

import akka.stream.scaladsl.Flow
import com.freelanceStats.commons.models.UnsavedRawJob
import com.freelanceStats.models.{Page, PageMetadata}

trait JobCreatorFactory[Metadata <: PageMetadata] {
  def create: Flow[Page[Metadata], UnsavedRawJob, _]
}
