package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.transport.api.matchers.RequestMatcher

private[transport] case class SubKey(groupName: Option[String], requestMatcher: RequestMatcher)