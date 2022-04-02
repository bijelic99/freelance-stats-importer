package com.freelanceStats.configurations.sources

import com.freelanceStats.s3Client.models.FileReference
import com.typesafe.config.ConfigFactory
import org.joda.time.Period

import scala.concurrent.duration.{FiniteDuration, Duration => ScalaDuration}
import scala.util.chaining._

class FreelancerSourceConfiguration {

  private val configuration = ConfigFactory.load()

  val url: String =
    configuration
      .getString("sources.freelancer.url")

  val sourceThrottleElements: Int =
    configuration
      .getInt("sources.freelancer.sourceThrottle.elements")

  val sourceThrottlePer: FiniteDuration =
    configuration
      .getString("sources.freelancer.sourceThrottle.per")
      .pipe(ScalaDuration.create)
      .pipe {
        case duration: FiniteDuration =>
          duration
        case _ =>
          throw new Exception("Cant be infinite duration")
      }
}
