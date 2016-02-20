package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.transport.api.matchers.TextMatcher
import eu.inn.hyperbus.transport.api.uri.UriParts

private[transport] case class SubKey(groupName: Option[String], args: Map[String, TextMatcher]) {
  def matchArgs(other: Map[String, TextMatcher]) = UriParts.matchUriParts(args, other)
}
