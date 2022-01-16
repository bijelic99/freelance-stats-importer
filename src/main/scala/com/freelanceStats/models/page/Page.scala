package com.freelanceStats.models.page

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.freelanceStats.models.page.Page.PageContents
import com.freelanceStats.models.pageMetadata.ProgressMetadata

trait Page[+Metadata <: ProgressMetadata] {
  def metadata: Metadata
  def contents: PageContents
}

object Page {
  type PageContents = Source[ByteString, _]
}
