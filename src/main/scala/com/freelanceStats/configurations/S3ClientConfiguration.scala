package com.freelanceStats.configurations

import com.typesafe.config.ConfigFactory

import scala.util.Try

class S3ClientConfiguration
    extends com.freelancerStats.amazonAsyncS3Client.configurations.S3ClientConfiguration {

  private val configuration = ConfigFactory.load()

  override val accessKey: Option[String] = Try(
    configuration.getString("s3.accessKey")
  ).toOption

  override val secretAccessKey: Option[String] = Try(
    configuration.getString("s3.secretAccessKey")
  ).toOption

  override val endpoint: Option[String] = Try(
    configuration.getString("s3.endpoint")
  ).toOption

  override val region: Option[String] = Try(
    configuration.getString("s3.region")
  ).toOption
}
