package com.hypertino.hyperbus.transport.distributedakka

import scala.concurrent.duration._

private[transport] object Util {
  val defaultTimeout = 20.second // todo: move to config

  // empty group doesn't work, so we need to have some default string
  def getUniqGroupName(groupName: Option[String]): Option[String] = {
    val defaultGroupName = "-default-"
    groupName.map { s ⇒
      if (s.startsWith(defaultGroupName))
        defaultGroupName + s
      else
        s
    } orElse {
      Some(defaultGroupName)
    }
  }
}
