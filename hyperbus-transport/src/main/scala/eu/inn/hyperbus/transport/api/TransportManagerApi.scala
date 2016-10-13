package eu.inn.hyperbus.transport.api

import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import rx.lang.scala.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * This is an API to manage generic transport layer.
  * Has no knowledge about underlying data model.
  */

trait TransportManagerApi {
  def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse]

  def publish(message: TransportRequest): Future[PublishResult]

  def onCommand(requestMatcher: RequestMatcher,
                inputDeserializer: RequestDeserializer[Request[Body]])
               (handler: (Request[Body]) => Future[TransportResponse]): Future[Subscription]

  def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
              groupName: String,
              inputDeserializer: RequestDeserializer[REQ],
              subscriber: Subscriber[REQ]): Future[Subscription]

  def off(subscription: Subscription): Future[Unit]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}
