package eu.inn.hyperbus.transport.api

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * This is an API to manage generic transport layer.
 * Has no knowledge about underlying data model.
 */

trait TransportManagerApi {
  def ask[OUT <: TransportResponse](message: TransportRequest,outputDecoder: Decoder[OUT]): Future[OUT]

  def publish(message: TransportRequest): Future[Unit]

  def process[IN <: TransportRequest](topicFilter: Topic,
                                      inputDecoder: Decoder[IN],
                                      exceptionEncoder: Encoder[Throwable])
                                     (handler: (IN) => Future[TransportResponse]): String

  def subscribe[IN <: TransportRequest ](topicFilter: Topic, groupName: String,
                                         inputDecoder: Decoder[IN])
                                        (handler: (IN) => Future[Unit]): String

  def off(subscriptionId: String): Unit

  def shutdown(duration: FiniteDuration): Future[Boolean]
}
