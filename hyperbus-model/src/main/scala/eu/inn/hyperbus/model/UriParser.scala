package eu.inn.hyperbus.model

import scala.collection.mutable

object UriParser {
  def extractParameters(uri: String): Seq[String] = {
    val DEFAULT = 0
    val ARG = 1
    var state = DEFAULT
    val result = new mutable.MutableList[String]
    val buf = new mutable.StringBuilder

    uri.foreach { c ⇒
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
}
