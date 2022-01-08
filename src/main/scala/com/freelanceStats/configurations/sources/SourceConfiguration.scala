package com.freelanceStats.configurations.sources

import com.freelanceStats.s3Client.models.FileReference

trait SourceConfiguration {
  def lastPageMetadataFile: FileReference
}
