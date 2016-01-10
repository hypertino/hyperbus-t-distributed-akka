package eu.inn.hyperbus.transport.api

import com.typesafe.config.ConfigValue
import eu.inn.binders.core.{ImplicitSerializer, ImplicitDeserializer}
import eu.inn.binders.json.{JsonSerializer, JsonDeserializer}
import eu.inn.binders.naming.{PlainConverter, Converter}

case class Uri(pattern: UriPart, parts: UriParts = UriParts.empty) {
  def matchUri(other: Uri): Boolean = pattern.matchUriPart(other.pattern) &&
    parts.matchUriParts(other.parts)

  override def toString = s"Uri($pattern$partsFormat)"

  private [this] def partsFormat =
    if (parts.uriPartsMap.isEmpty) ""
  else
    parts.uriPartsMap.mkString("#", ",", "")

  private [api] def toPojoJson = {
    val m = parts.uriPartsMap.map {
      case (key, value) ⇒ key → value.specific
    }
    UriPojoJson(pattern.specific, if (m.isEmpty) None else Some(m))
  }
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
    apply(pojo)
  }

  private [api] def apply(pojo: UriPojo): Uri = {
    Uri(
      UriPart(pojo.pattern),
      pojo.parts.map { parts ⇒
        UriParts(parts.map { case (k, v) ⇒
          k → UriPart(v)
        })
      } getOrElse {
        UriParts.empty
      }
    )
  }

  private [api] def apply(pojo: UriPojoJson): Uri = {
    apply(pojo.pattern, pojo.parts.getOrElse(Map.empty))
  }
}

private[api] case class UriPojo(pattern: UriPartPojo, parts: Option[Map[String, UriPartPojo]])
private[api] case class UriPojoJson(pattern: String, parts: Option[Map[String, String]])

// todo: use generic type instead PlainConverter
class UriJsonDeserializer extends ImplicitDeserializer[Uri, JsonDeserializer[PlainConverter]] {
  import eu.inn.binders._
  override def read(deserializer: JsonDeserializer[PlainConverter]): Uri = Uri(deserializer.unbind[UriPojoJson])
}

class UriJsonSerializer extends ImplicitSerializer[Uri, JsonSerializer[PlainConverter]] {
  import eu.inn.binders._
  override def write(serializer: JsonSerializer[PlainConverter], value: Uri) = serializer.bind(value.toPojoJson)
}