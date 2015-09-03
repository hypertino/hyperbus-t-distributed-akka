package eu.inn.hyperbus.transport.api

import com.typesafe.config.ConfigValue

import scala.util.matching.Regex

sealed trait Filter {
  def matchFilter(other: Filter): Boolean

  def specific: String = this match {
    case SpecificValue(value) ⇒ value
    case _ ⇒ throw new UnsupportedOperationException(s"Specific value expected but got $getClass")
  }
}

case object AnyValue extends Filter {
  def matchFilter(other: Filter) = true
}

case class SpecificValue(value: String) extends Filter {
  def matchFilter(other: Filter) = other match {
    case SpecificValue(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchFilter(this)
  }
}

case class RegexFilter(value: String) extends Filter {
  lazy val valueRegex = new Regex(value)

  def matchFilter(other: Filter) = other match {
    case SpecificValue(otherValue) ⇒ valueRegex.findFirstMatchIn(otherValue).isDefined
    case RegexFilter(otherRegexValue) ⇒ otherRegexValue == value
    case _ ⇒ other.matchFilter(this)
  }
}

case class Filters(filterMap: Map[String, Filter]) {
  def matchFilters(other: Filters): Boolean = {
    filterMap.map { case (k, v) ⇒
      other.filterMap.get(k).map { av ⇒
        v.matchFilter(av)
      } getOrElse {
        v == AnyValue
      }
    }.forall(r => r)
  }
}

object Filter {
  def apply(configValue: ConfigValue): Filter = {
    import eu.inn.binders.tconfig._
    apply(configValue.read[FilterPojo])
  }

  def apply(value: Option[String], matchType: Option[String]): Filter = matchType match {
    case Some("Any") ⇒ AnyValue
    case Some("Regex") ⇒ RegexFilter(value.getOrElse(
      throw new TransportConfigurationError("Please provide value for Regex partition argument"))
    )
    case _ ⇒ SpecificValue(value.getOrElse(
      throw new TransportConfigurationError("Please provide value for Exact partition argument"))
    )
  }

  private [api] def apply(pojo: FilterPojo): Filter = apply(pojo.value, pojo.matchType)
}

private [api] case class FilterPojo(value: Option[String], matchType: Option[String])

object Filters {
  val empty = Filters(Map.empty)
}