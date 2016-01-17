package eu.inn.hyperbus.transport.api.uri

import scala.collection.mutable

sealed trait Token
case object SlashToken extends Token
case class TextToken(value: String) extends Token
case class ParameterToken(value: String) extends Token

object UriParser {
  def extractParameters(uriPattern: String): Seq[String] = tokens(uriPattern) collect {
    case ParameterToken(str) ⇒ str
  }

  // todo: make this lazy (iterator of Token?)
  def tokens(uriPattern: String): Seq[Token] = {
    val DEFAULT = 0
    val PARAMETER = 1
    var state = DEFAULT
    val result = new mutable.MutableList[Token]
    val buf = new mutable.StringBuilder

    uriPattern.foreach { c ⇒
      state match {
        case DEFAULT ⇒
          c match {
            case '{' ⇒
              if (buf.nonEmpty) {
                result += TextToken(buf.toString())
                buf.clear()
              }
              state = PARAMETER
            case '/' ⇒
              if (buf.nonEmpty) {
                result += TextToken(buf.toString())
                buf.clear()
              }
              result += SlashToken
            case _ ⇒
              buf += c
          }
        case PARAMETER ⇒
          c match {
            case '}' ⇒
              result += ParameterToken(buf.toString())
              buf.clear()
              state = DEFAULT
            case _ ⇒
              buf += c
          }
      }
    }
    if (buf.nonEmpty) {
      result += TextToken(buf.toString())
    }

    result.toSeq
  }
}
