package com.hypertino.hyperbus.transport.api.uri

import com.typesafe.config.ConfigValue
import com.hypertino.binders.core.{BindOptions, ImplicitDeserializer, ImplicitSerializer}
import com.hypertino.binders.json.{JsonDeserializer, JsonSerializer}
import com.hypertino.inflector.naming.PlainConverter
import com.hypertino.hyperbus.transport.api.matchers.{Specific, TextMatcher, TextMatcherPojo}

import scala.language.postfixOps

case class Uri(pattern: TextMatcher, args: Map[String, TextMatcher]) {
  def matchUri(other: Uri): Boolean = pattern.matchText(other.pattern) &&
    Uri.matchUriParts(args, other.args)

  override def toString = s"Uri($pattern$argsFormat)"

  def matchArgs(other: Map[String, TextMatcher]) = Uri.matchUriParts(args, other)

  private[this] def argsFormat =
    if (args.isEmpty) ""
    else
      args.mkString("#", ",", "")

  private[api] def toPojoJson = {
    val m = args.map {
      case (key, value) ⇒ key → value.specific
    }
    UriPojoJson(pattern.specific, if (m.isEmpty) None else Some(m))
  }

  lazy val formatted: String = UriParser.tokens(pattern.specific) collect {
    case TextToken(s) ⇒ s
    case SlashToken ⇒ '/'
    case ParameterToken(parameterName, _) ⇒ args(parameterName).specific
  } mkString
}

object Uri {
  def apply(pattern: TextMatcher): Uri = Uri(pattern, Map.empty[String, TextMatcher])

  def apply(pattern: String): Uri = Uri(Specific(pattern))

  def apply(pattern: String, args: Map[String, String]): Uri = Uri(Specific(pattern), args.map {
    case (k, v) ⇒ k → Specific(v)
  })

  def apply(configValue: ConfigValue): Uri = {
    import com.hypertino.binders.config.ConfigBinders._
    val pojo = configValue.read[UriPojo]
    apply(pojo)
  }

  private[transport] def apply(pojo: UriPojo): Uri = {
    Uri(
      TextMatcher(pojo.pattern),
      pojo.args.map { args ⇒
        args.map { case (k, v) ⇒
          k → TextMatcher(v)
        }
      } getOrElse {
        Map.empty[String, TextMatcher]
      }
    )
  }

  private[transport] def apply(pojo: UriPojoJson): Uri = {
    apply(pojo.pattern, pojo.args.getOrElse(Map.empty).map(kv ⇒ kv._1 → kv._2))
  }

  def matchUriParts(a: Map[String, TextMatcher], b: Map[String, TextMatcher]): Boolean = {
    a.map { case (k, v) ⇒
      b.get(k).map { av ⇒
        v.matchText(av)
      } getOrElse {
        false // was: v == AnyValue
      }
    }.forall(r => r)
  }
}

private[transport] case class UriPojo(pattern: TextMatcherPojo, args: Option[Map[String, TextMatcherPojo]])

private[transport] case class UriPojoJson(pattern: String, args: Option[Map[String, String]])

// todo: use generic type instead PlainConverter
class UriJsonDeserializer(implicit val bindOptions: BindOptions) extends ImplicitDeserializer[Uri, JsonDeserializer[PlainConverter.type]] {
  override def read(deserializer: JsonDeserializer[PlainConverter.type]): Uri = Uri(deserializer.unbind[UriPojoJson])
}

class UriJsonSerializer(implicit val bindOptions: BindOptions) extends ImplicitSerializer[Uri, JsonSerializer[PlainConverter.type]] {
  override def write(serializer: JsonSerializer[PlainConverter.type], value: Uri) = serializer.bind(value.toPojoJson)
}