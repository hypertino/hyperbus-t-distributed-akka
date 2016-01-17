package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.transport.api.uri.{UriParts, UriPart}

private[transport] case class SubKey(groupName: Option[String], args: Map[String, UriPart]) {
  def matchArgs(other: Map[String, UriPart]) = UriParts.matchUriParts(args, other)
}
