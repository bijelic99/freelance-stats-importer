package com.freelanceStats.modules.queues

import akka.stream.alpakka.amqp.AmqpUriConnectionProvider
import com.freelanceStats.configurations.AlpakkaRabbitMQClientConfiguration
import com.google.inject.{AbstractModule, Provides}

class UriConnectionProviderModule extends AbstractModule {

  @Provides
  def connectionProvider(
      configuration: AlpakkaRabbitMQClientConfiguration
  ): AmqpUriConnectionProvider =
    AmqpUriConnectionProvider.create(configuration.url)
}
