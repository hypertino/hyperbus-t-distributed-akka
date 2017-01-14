package com.hypertino.hyperbus.transport.inproc

import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher

private[transport] case class SubKey(groupName: Option[String], requestMatcher: RequestMatcher)