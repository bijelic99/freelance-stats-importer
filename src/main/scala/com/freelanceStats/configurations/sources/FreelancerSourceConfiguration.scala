package com.freelanceStats.configurations.sources

import com.freelanceStats.s3Client.models.FileReference
import com.typesafe.config.ConfigFactory
import org.joda.time.Period

import scala.concurrent.duration.{FiniteDuration, Duration => ScalaDuration}
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

  val maxFetchOffset: Period =
    configuration
      .getString("sources.freelancer.maxFetchOffset")
      .pipe(Period.parse)

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

  val resultsPerPageLimit: Int =
    configuration
      .getInt("sources.freelancer.resultsPerPageLimit")
}
