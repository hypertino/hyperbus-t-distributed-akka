package eu.inn.servicebus.transport.inproc

import eu.inn.servicebus.transport.Filters

private[transport] case class SubKey(groupName: Option[String], partitionArgs: Filters)
