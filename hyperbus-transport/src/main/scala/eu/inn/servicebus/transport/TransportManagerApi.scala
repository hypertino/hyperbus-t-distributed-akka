package eu.inn.servicebus.transport

import eu.inn.servicebus.serialization._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * This is an API to manage generic transport layer.
 * Has no knowledge about underlying data model.
 */

trait TransportManagerApi {
  def ask[OUT, IN](
                    topic: Topic,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT]

  def publish[IN](
                   topic: Topic,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[Unit]

  def process[OUT, IN](topic: TopicFilter, inputDecoder: Decoder[IN],
                  partitionArgsExtractor: FilterArgsExtractor[IN],
                  exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: TopicFilter, groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: FilterArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String

  def off(subscriptionId: String): Unit

  def shutdown(duration: FiniteDuration): Future[Boolean]
}
