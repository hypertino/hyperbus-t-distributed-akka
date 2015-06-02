package eu.inn.servicebus.transport

import akka.actor.ActorSystem
import scala.concurrent.duration
import duration._

private [transport] object Util {
  val defaultTimeout = 30.second
  val defaultEncoding = "UTF-8"
  lazy val akkaSystem = ActorSystem("eu-inn-distrib-akka")

  // empty group doesn't work, so we need to have some default string
  def getUniqGroupName(groupName: Option[String]): Option[String] = {
    val defaultGroupName = "-default-"
    groupName.map{ s ⇒
      if (s.startsWith(defaultGroupName))
        defaultGroupName + s
      else
        s
    } orElse {
      Some(defaultGroupName)
    }
  }
}
