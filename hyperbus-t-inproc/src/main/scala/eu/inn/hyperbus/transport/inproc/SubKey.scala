package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.transport.api.UriParts

private[transport] case class SubKey(groupName: Option[String], parts: UriParts)
