package com.freelanceStats.components.queue

import akka.stream.alpakka.amqp.{
  AmqpConnectionProvider,
  AmqpUriConnectionProvider
}
import com.freelanceStats.alpakkaRabbitMQClient.connectionProviderFactory.ConnectionProviderFactory
import com.freelanceStats.configurations.AlpakkaRabbitMQClientConfiguration

import javax.inject.Inject

class UriConnectionProviderFactory @Inject() (
    configuration: AlpakkaRabbitMQClientConfiguration
) extends ConnectionProviderFactory {
  override def get: AmqpConnectionProvider =
    AmqpUriConnectionProvider.create(configuration.url)
}
