package eu.inn.hyperbus.transport.api

import com.typesafe.config.ConfigValue

case class Topic(url: Filter, extra: Filters = Filters.empty) {
  def matchTopic(other: Topic): Boolean = url.matchFilter(other.url) &&
    extra.matchFilters(other.extra)

  override def toString = s"Topic($url$extraFiltersFormat)"

  private [this] def extraFiltersFormat =
    if (extra.filterMap.isEmpty) ""
  else
    extra.filterMap.mkString("#", ",", "")
}

object Topic {
  def apply(url: String): Topic = Topic(SpecificValue(url), Filters.empty)

  def apply(url: String, extra: Filters): Topic = Topic(SpecificValue(url), extra)

  def apply(url: String, extra: Map[String,String]): Topic = Topic(SpecificValue(url), Filters(extra.map{ case (k,v) ⇒
      k -> SpecificValue(v)
  }))

  def apply(configValue: ConfigValue): Topic = {
    import eu.inn.binders.tconfig._
    val pojo = configValue.read[TopicPojo]
    Topic(
      Filter(pojo.url),
      pojo.extra.map { extraFilters ⇒
        Filters(extraFilters.map { case (k,v) ⇒
          k → Filter(v)
        })
      } getOrElse {
        Filters.empty
      }
    )
  }
}

private[api] case class TopicPojo(url: FilterPojo, extra: Option[Map[String, FilterPojo]])