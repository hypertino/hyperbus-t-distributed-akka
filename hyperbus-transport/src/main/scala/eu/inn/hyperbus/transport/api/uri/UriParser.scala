package eu.inn.hyperbus.transport.api.uri

import scala.collection.mutable

sealed trait Token
case object SlashToken extends Token
case class TextToken(value: String) extends Token
case class ParameterToken(value: String, matchType: MatchType = RegularMatchType) extends Token

sealed trait MatchType
case object RegularMatchType extends MatchType
case object PathMatchType extends MatchType

case class UrlParserException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)

object UriParser {
  def extractParameters(uriPattern: String): Seq[String] = tokens(uriPattern) collect {
    case ParameterToken(str, _) ⇒ str
  }

  // todo: make this lazy (iterator of Token?)
  def tokens(uriPattern: String): Seq[Token] = {
    val DEFAULT = 0
    val PARAMETER = 1
    val PARAMETER_MATCH_TYPE = 2
    var state = DEFAULT
    val result = new mutable.MutableList[Token]
    val buf = new mutable.StringBuilder
    var parameterName: String = null

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
            case ':' ⇒
              parameterName = buf.toString()
              buf.clear()
              state = PARAMETER_MATCH_TYPE
            case _ ⇒
              buf += c
          }
        case PARAMETER_MATCH_TYPE ⇒
          c match {
            case '}' ⇒
              result += ParameterToken(parameterName, matchType(buf.toString()))
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

  def matchType(s: String): MatchType = s match {
    case "*" ⇒ PathMatchType
    case "@" ⇒ RegularMatchType
    case _ ⇒ throw new UrlParserException(s"Unexpected match type: $s")
  }
}
