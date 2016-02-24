package eu.inn.hyperbus.transport.api

import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * This is an API to manage generic transport layer.
 * Has no knowledge about underlying data model.
 */

trait TransportManagerApi {
  def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse]

  def publish(message: TransportRequest): Future[PublishResult]

  def onCommand(requestMatcher: TransportRequestMatcher,
                                        inputDeserializer: Deserializer[TransportRequest])
                                       (handler: (TransportRequest) => Future[TransportResponse]): Future[Subscription]

  def onEvent(requestMatcher: TransportRequestMatcher,
                                      groupName: String,
                                      inputDeserializer: Deserializer[TransportRequest])
                                     (handler: (TransportRequest) => Future[Unit]): Future[Subscription]

  def off(subscription: Subscription): Future[Unit]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}
