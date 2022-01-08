package com.freelanceStats.components.dataSourceFactory

import akka.stream.scaladsl.Source
import com.freelanceStats.models.{Page, PageMetadata}

trait DataSourceFactory[Metadata <: PageMetadata] {
  def create: Source[Page[Metadata], _]
}
