package com.hypertino.hyperbus.transport.api.matchers

import com.typesafe.config.ConfigValue
import com.hypertino.binders.annotations.fieldName
import com.hypertino.hyperbus.transport.api.TransportConfigurationError

import scala.util.matching.Regex

sealed trait TextMatcher {
  def matchText(other: TextMatcher): Boolean

  def specific: String = this match {
    case Specific(value) ⇒ value
    case _ ⇒ throw new UnsupportedOperationException(s"Specific value expected but got $getClass")
  }
}

object TextMatcher {
  def apply(configValue: ConfigValue): TextMatcher = {
    import com.hypertino.binders.config.ConfigBinders._
    apply(configValue.read[TextMatcherPojo])
  }

  def apply(value: Option[String], matchType: Option[String]): TextMatcher = matchType match {
    case Some("Any") ⇒ Any
    case Some("Regex") ⇒ RegexMatcher(value.getOrElse(
      throw new TransportConfigurationError("Please provide value for Regex matcher"))
    )
    case Some("Specific") | None ⇒ Specific(value.getOrElse(
      throw new TransportConfigurationError("Please provide value for Specific matcher"))
    )
    case other ⇒
      throw new TransportConfigurationError(s"Unsupported TextMatcher: $other")
  }

  private[api] def apply(pojo: TextMatcherPojo): TextMatcher = apply(pojo.value, pojo.matchType)
}

case object Any extends TextMatcher {
  def matchText(other: TextMatcher) = true
}

case class RegexMatcher(value: String) extends TextMatcher {
  lazy val valueRegex = new Regex(value)

  def matchText(other: TextMatcher) = other match {
    case Specific(otherValue) ⇒ valueRegex.findFirstMatchIn(otherValue).isDefined
    case RegexMatcher(otherRegexValue) ⇒ otherRegexValue == value
    case _ ⇒ other.matchText(this)
  }
}

case class Specific(value: String) extends TextMatcher {
  def matchText(other: TextMatcher) = other match {
    case Specific(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchText(this)
  }
}

private[api] case class TextMatcherPojo(value: Option[String], @fieldName("type") matchType: Option[String])
