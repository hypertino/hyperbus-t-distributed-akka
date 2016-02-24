package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher

private[transport] case class SubKey(groupName: Option[String], requestMatcher: TransportRequestMatcher)