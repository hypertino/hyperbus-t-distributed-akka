package eu.inn.hyperbus.transport.api

import com.typesafe.config.ConfigValue

import scala.util.matching.Regex

sealed trait UriPart {
  def matchUriPart(other: UriPart): Boolean

  def specific: String = this match {
    case SpecificValue(value) ⇒ value
    case _ ⇒ throw new UnsupportedOperationException(s"Specific value expected but got $getClass")
  }
}

case object AnyValue extends UriPart {
  def matchUriPart(other: UriPart) = true
}

case class SpecificValue(value: String) extends UriPart {
  def matchUriPart(other: UriPart) = other match {
    case SpecificValue(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchUriPart(this)
  }
}

case class RegexUriPart(value: String) extends UriPart {
  lazy val valueRegex = new Regex(value)

  def matchUriPart(other: UriPart) = other match {
    case SpecificValue(otherValue) ⇒ valueRegex.findFirstMatchIn(otherValue).isDefined
    case RegexUriPart(otherRegexValue) ⇒ otherRegexValue == value
    case _ ⇒ other.matchUriPart(this)
  }
}

case class UriParts(uriPartsMap: Map[String, UriPart]) {
  def matchUriParts(other: UriParts): Boolean = {
    uriPartsMap.map { case (k, v) ⇒
      other.uriPartsMap.get(k).map { av ⇒
        v.matchUriPart(av)
      } getOrElse {
        v == AnyValue
      }
    }.forall(r => r)
  }
}

object UriPart {
  def apply(configValue: ConfigValue): UriPart = {
    import eu.inn.binders.tconfig._
    apply(configValue.read[UriPartPojo])
  }

  def apply(value: Option[String], matchType: Option[String]): UriPart = matchType match {
    case Some("Any") ⇒ AnyValue
    case Some("Regex") ⇒ RegexUriPart(value.getOrElse(
      throw new TransportConfigurationError("Please provide value for Regex URI part"))
    )
    case Some("Specific") ⇒ SpecificValue(value.getOrElse(
      throw new TransportConfigurationError("Please provide value for Specific URI part"))
    )
    case other ⇒
      throw new TransportConfigurationError(s"Unsupport URI part: $other")
  }

  private [api] def apply(pojo: UriPartPojo): UriPart = apply(pojo.value, pojo.matchType)
}

private [api] case class UriPartPojo(value: Option[String], matchType: Option[String])

object UriParts {
  val empty = UriParts(Map.empty)
}