package eu.inn.hyperbus.transport.kafkatransport

import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import rx.lang.scala.Subscriber

private[transport] case class UnderlyingSubscription[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                                    inputDeserializer: RequestDeserializer[REQ],
                                                    subscriber: Subscriber[_])
