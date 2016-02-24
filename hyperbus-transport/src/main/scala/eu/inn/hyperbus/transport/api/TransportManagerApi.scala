package eu.inn.hyperbus.transport.api

import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * This is an API to manage generic transport layer.
 * Has no knowledge about underlying data model.
 */

trait TransportManagerApi {
  def ask[OUT <: TransportResponse](message: TransportRequest, outputDeserializer: Deserializer[OUT]): Future[OUT]

  def publish(message: TransportRequest): Future[PublishResult]

  def onCommand[IN <: TransportRequest](requestMatcher: TransportRequestMatcher,
                                        inputDeserializer: Deserializer[IN])
                                       (handler: (IN) => Future[TransportResponse]): String

  def onEvent[IN <: TransportRequest](requestMatcher: TransportRequestMatcher,
                                      groupName: String,
                                      inputDeserializer: Deserializer[IN])
                                     (handler: (IN) => Future[Unit]): String

  def off(subscriptionId: String): Unit

  def shutdown(duration: FiniteDuration): Future[Boolean]
}
