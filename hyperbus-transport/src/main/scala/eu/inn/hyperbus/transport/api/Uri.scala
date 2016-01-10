package eu.inn.hyperbus.transport.api

import com.typesafe.config.ConfigValue

case class Uri(pattern: UriPart, parts: UriParts = UriParts.empty) {
  def matchUri(other: Uri): Boolean = pattern.matchUriPart(other.pattern) &&
    parts.matchUriParts(other.parts)

  override def toString = s"Uri($pattern$partsFormat)"

  private [this] def partsFormat =
    if (parts.uriPartsMap.isEmpty) ""
  else
    parts.uriPartsMap.mkString("#", ",", "")
}

object Uri {
  def apply(pattern: String): Uri = Uri(SpecificValue(pattern), UriParts.empty)

  def apply(pattern: String, parts: UriParts): Uri = Uri(SpecificValue(pattern), parts)

  def apply(pattern: String, parts: Map[String,String]): Uri = Uri(SpecificValue(pattern), UriParts(parts.map{ case (k,v) ⇒
      k -> SpecificValue(v)
  }))

  def apply(configValue: ConfigValue): Uri = {
    import eu.inn.binders.tconfig._
    val pojo = configValue.read[UriPojo]
    Uri(
      UriPart(pojo.pattern),
      pojo.parts.map { parts ⇒
        UriParts(parts.map { case (k,v) ⇒
          k → UriPart(v)
        })
      } getOrElse {
        UriParts.empty
      }
    )
  }
}

private[api] case class UriPojo(pattern: UriPartPojo, parts: Option[Map[String, UriPartPojo]])