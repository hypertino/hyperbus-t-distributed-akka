package eu.inn.hyperbus.transport.kafkatransport

import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher

import scala.concurrent.Future

private[transport] case class UnderlyingSubscription(requestMatcher: RequestMatcher,
                                  inputDeserializer: RequestDeserializer[Request[Body]],
                                  handler: (Request[Body]) ⇒ Future[Unit]
                                 )
