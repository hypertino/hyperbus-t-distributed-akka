package eu.inn.hyperbus.transport.api.matchers

import com.typesafe.config.{ConfigValue, Config}
import eu.inn.hyperbus.transport.api.TransportRequest
import eu.inn.hyperbus.transport.api.uri.{UriPojo, Uri}

case class TransportRequestMatcher(uri: Option[Uri], headers: Map[String, TextMatcher]) {

  // strict matching for concrete message
  def matchMessage(message: TransportRequest): Boolean = {
    uri.exists(_.matchUri(message.uri)) &&
      headers.map { case (headerName, headerMatcher) ⇒
        message.headers.get(headerName).map { header ⇒
          header.exists(headerText ⇒ headerMatcher.matchText(SpecificValue(headerText)))
        } getOrElse {
          false
        }
      }.forall(r => r)
  }

  // wide match for routing
  def matchRequestMatcher(other: TransportRequestMatcher): Boolean = {
    (uri.isEmpty || other.uri.isEmpty || uri.get.matchUri(other.uri.get)) &&
      headers.map { case (headerName, headerMatcher) ⇒
        other.headers.get(headerName).map { header ⇒
          headerMatcher.matchText(header)
        } getOrElse {
          true
        }
      }.forall(r => r)
  }
}

object TransportRequestMatcher {
  def apply(uri: Option[Uri]) : TransportRequestMatcher = TransportRequestMatcher(uri, Map.empty)

  private [transport] def apply(config: ConfigValue): TransportRequestMatcher = {
    import eu.inn.binders.tconfig._
    val pojo = config.read[TransportRequestMatcherPojo]
    apply(pojo)
  }

  private [transport] def apply(pojo: TransportRequestMatcherPojo): TransportRequestMatcher = {
    TransportRequestMatcher(pojo.uri.map(Uri.apply), pojo.headers.map { case (k, v) ⇒
      k → TextMatcher(v)
    })
  }
}

private [transport] case class TransportRequestMatcherPojo(uri: Option[UriPojo], headers: Map[String, TextMatcherPojo])
