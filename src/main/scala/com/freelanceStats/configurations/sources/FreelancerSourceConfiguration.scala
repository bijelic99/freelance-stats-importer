package com.freelanceStats.configurations.sources

import com.freelanceStats.s3Client.models.FileReference
import com.typesafe.config.ConfigFactory
import org.joda.time.Duration

import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}
import scala.util.Try
import scala.util.chaining._

class FreelancerSourceConfiguration extends SourceConfiguration {

  private val configuration = ConfigFactory.load()

  override val lastPageMetadataFile: FileReference = {
    val fileReferenceConfiguration =
      configuration.getConfig("sources.freelancer.lastPageMetadataFile")
    FileReference(
      bucket = fileReferenceConfiguration.getString("bucket"),
      key = fileReferenceConfiguration.getString("key")
    )
  }

  val maxFetchOffset: Duration =
    configuration
      .getString("sources.freelancer.maxFetchOffset")
      .pipe(Duration.parse)

  val url: String =
    configuration
      .getString("sources.freelancer.url")

  val frequency: FiniteDuration =
    configuration
      .getString("sources.freelancer.frequency")
      .pipe(ScalaDuration.create)
      .pipe {
        case duration: FiniteDuration =>
          duration
        case _ =>
          throw new Exception("Cant be infinite duration")
      }
}
