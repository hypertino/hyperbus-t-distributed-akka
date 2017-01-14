package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher

case class TransportRoute[T](transport: T, matcher: RequestMatcher)

