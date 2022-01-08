package com.freelanceStats.configurations

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.Try

class AlpakkaRabbitMQClientConfiguration
    extends com.freelanceStats.alpakkaRabbitMQClient.configuration.AlpakkaRabbitMQClientConfiguration {

  private val configuration = ConfigFactory.load()

  override val url: String = configuration.getString("queues.raw-job-queue.url")

  override val queueName: String =
    configuration.getString("queues.raw-job-queue.queueName")

  override val writeBufferSize: Int =
    Try(configuration.getInt("queues.raw-job-queue.writeBufferSize")).toOption
      .getOrElse(10)

  override val writeConfirmationTimeout: FiniteDuration =
    Try(
      configuration.getString("queues.raw-job-queue.writeConfirmationTimeout")
    ).toOption
      .map(Duration.create)
      .collect { case duration: FiniteDuration =>
        duration
      }
      .getOrElse(1.second)

  override val readBufferSize: Int =
    Try(configuration.getInt("queues.raw-job-queue.readBufferSize")).toOption
      .getOrElse(10)
}
