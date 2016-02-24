package eu.inn.hyperbus.transport.api

import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher

case class TransportRoute[T](transport: T, matcher: TransportRequestMatcher)

