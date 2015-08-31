package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.transport.api.Filters

private[transport] case class SubKey(groupName: Option[String], partitionArgs: Filters)
