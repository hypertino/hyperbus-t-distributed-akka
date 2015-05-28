package eu.inn.hyperbus.impl

import eu.inn.servicebus.transport.{AnyValue, PartitionArgs, Topic}

import scala.collection.mutable

object Helpers {
  def extractParametersFromUrl(url: String): Seq[String] = {
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
          }
        case ARG ⇒
          c match {
            case '}' ⇒
              result += buf.toString()
              buf.clear()
              state = DEFAULT
            case _ ⇒
              buf += c
          }
      }
    }

    result.toSeq
  }

  def topicWithAllPartitions(url: String): Topic = Topic(url, PartitionArgs(extractParametersFromUrl(url).map(_ → AnyValue).toMap))
}
