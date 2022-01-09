package com.freelanceStats.configurations

import com.typesafe.config.ConfigFactory

class ApplicationConfiguration {

  private val configuration = ConfigFactory.load()

  val source: String = configuration.getString("application.source")

  val bucket: String = configuration.getString("application.bucket")

}
