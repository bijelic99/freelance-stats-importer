package com.freelanceStats.configurations

import com.typesafe.config.ConfigFactory

class ElasticConfiguration {

  private val configuration = ConfigFactory.load()

  val endpoint: String = configuration.getString("elastic.endpoint")

  val jobIndex: String = configuration.getString("elastic.jobIndex")

}
