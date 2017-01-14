package com.hypertino.hyperbus.transport.kafkatransport

import com.hypertino.hyperbus.model.{Body, Request}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import rx.lang.scala.Observer

private[transport] case class UnderlyingSubscription[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                                    inputDeserializer: RequestDeserializer[REQ],
                                                    observer: Observer[REQ])
