package eu.inn.hyperbus.transport.api

import eu.inn.hyperbus.transport.api.matchers.RequestMatcher

case class TransportRoute[T](transport: T, matcher: RequestMatcher)

