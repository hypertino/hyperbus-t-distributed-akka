package eu.inn.hyperbus.transport.api.uri

import eu.inn.hyperbus.transport.api.matchers.{TextMatcher, AnyValue}

object UriParts {
  def matchUriParts(a: Map[String, TextMatcher], b: Map[String, TextMatcher]): Boolean = {
    a.map { case (k, v) ⇒
      b.get(k).map { av ⇒
        v.matchText(av)
      } getOrElse {
        v == AnyValue
      }
    }.forall(r => r)
  }
}
