package com.freelanceStats.models

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.freelanceStats.models.Page.PageContents

trait Page[Metadata <: PageMetadata] {
  def metadata: Metadata
  def contents: PageContents
}

object Page {
  type PageContents = Source[ByteString, _]
}
