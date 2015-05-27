package eu.inn.hyperbus.impl

import eu.inn.servicebus.transport.{AnyValue, PartitionArgs, Topic}

import scala.collection.mutable

object Helpers {
  def parseUrl(url: String): Seq[String] = {
    val DEFAULT = 0
    val ARG = 1
    var state = DEFAULT
    val result = new mutable.MutableList[String]
    val buf = new mutable.StringBuilder

    url.foreach{c ⇒
      state match {
        case DEFAULT ⇒
          c match {
            case '{' ⇒
              state = ARG
            case _ ⇒
              buf.append(c)
          }
        case ARG ⇒
          c match {
            case '}' ⇒
              result += buf.toString()
              state = DEFAULT
            case _ ⇒
          }
      }
    }

    result.toSeq
  }

  def topicWithAllPartitions(url: String): Topic = Topic(url, PartitionArgs(parseUrl(url).map(_ → AnyValue).toMap))
}
