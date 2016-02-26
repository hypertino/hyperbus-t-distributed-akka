package eu.inn.hyperbus.transport.api.matchers

import com.typesafe.config.ConfigValue
import eu.inn.hyperbus.transport.api.TransportConfigurationError

import scala.util.matching.Regex

sealed trait TextMatcher {
  def matchText(other: TextMatcher): Boolean

  def specific: String = this match {
    case SpecificValue(value) ⇒ value
    case _ ⇒ throw new UnsupportedOperationException(s"Specific value expected but got $getClass")
  }
}

object TextMatcher {
  def apply(configValue: ConfigValue): TextMatcher = {
    import eu.inn.binders.tconfig._
    apply(configValue.read[TextMatcherPojo])
  }

  def apply(value: Option[String], matchType: Option[String]): TextMatcher = matchType match {
    case Some("Any") ⇒ AnyValue
    case Some("Regex") ⇒ RegexTextMatcher(value.getOrElse(
      throw new TransportConfigurationError("Please provide value for Regex TextMatcher"))
    )
    case Some("Specific") | None ⇒ SpecificValue(value.getOrElse(
      throw new TransportConfigurationError("Please provide value for Specific TextMatcher"))
    )
    case other ⇒
      throw new TransportConfigurationError(s"Unsupported TextMatcher: $other")
  }

  private[api] def apply(pojo: TextMatcherPojo): TextMatcher = apply(pojo.value, pojo.matchType)
}

case object AnyValue extends TextMatcher {
  def matchText(other: TextMatcher) = true
}

case class RegexTextMatcher(value: String) extends TextMatcher {
  lazy val valueRegex = new Regex(value)

  def matchText(other: TextMatcher) = other match {
    case SpecificValue(otherValue) ⇒ valueRegex.findFirstMatchIn(otherValue).isDefined
    case RegexTextMatcher(otherRegexValue) ⇒ otherRegexValue == value
    case _ ⇒ other.matchText(this)
  }
}

case class SpecificValue(value: String) extends TextMatcher {
  def matchText(other: TextMatcher) = other match {
    case SpecificValue(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchText(this)
  }
}

private[api] case class TextMatcherPojo(value: Option[String], matchType: Option[String])
