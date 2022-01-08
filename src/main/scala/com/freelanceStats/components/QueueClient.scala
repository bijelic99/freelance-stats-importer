package com.freelanceStats.components

import akka.stream.Materializer
import akka.util.ByteString
import com.freelanceStats.alpakkaRabbitMQClient.AlpakkaRabbitMQConsumer
import com.freelanceStats.alpakkaRabbitMQClient.connectionProviderFactory.ConnectionProviderFactory
import com.freelanceStats.alpakkaRabbitMQClient.elementConverter.ElementToByteStringConverter
import com.freelanceStats.commons.implicits.playJson.ModelsFormat
import com.freelanceStats.commons.models.RawJob
import com.freelanceStats.components.QueueClient.RawJobToByteStringConverter
import com.freelanceStats.configurations.AlpakkaRabbitMQClientConfiguration
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}

@Singleton
class QueueClient @Inject() (
    override val configuration: AlpakkaRabbitMQClientConfiguration,
    override val connectionProviderFactory: ConnectionProviderFactory
)(
    override implicit val materializer: Materializer
) extends AlpakkaRabbitMQConsumer[RawJob] {
  override def elementToByteStringConverter
      : ElementToByteStringConverter[RawJob] = RawJobToByteStringConverter
}

object QueueClient {
  object RawJobToByteStringConverter
      extends ElementToByteStringConverter[RawJob] {
    import ModelsFormat._

    override def toByteString(element: RawJob): ByteString =
      ByteString.fromString(Json.toJson(element).toString())
  }
}
