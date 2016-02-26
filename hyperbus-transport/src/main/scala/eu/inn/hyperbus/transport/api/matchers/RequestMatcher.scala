package eu.inn.hyperbus.transport.api.matchers

import com.typesafe.config.ConfigValue
import eu.inn.hyperbus.transport.api.TransportRequest
import eu.inn.hyperbus.transport.api.uri.{Uri, UriPojo}

case class RequestMatcher(uri: Option[Uri], headers: Map[String, TextMatcher]) {

  // strict matching for concrete message
  def matchMessage(message: TransportRequest): Boolean = {
    uri.exists(_.matchUri(message.uri)) &&
      headers.map { case (headerName, headerMatcher) ⇒
        message.headers.get(headerName).map { header ⇒
          header.exists(headerText ⇒ headerMatcher.matchText(Specific(headerText)))
        } getOrElse {
          false
        }
      }.forall(r => r)
  }

  // wide match for routing
  def matchRequestMatcher(other: RequestMatcher): Boolean = {
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

object RequestMatcher {
  def apply(uri: Option[Uri]): RequestMatcher = RequestMatcher(uri, Map.empty)

  private[transport] def apply(config: ConfigValue): RequestMatcher = {
    import eu.inn.binders.tconfig._
    val pojo = config.read[RequestMatcherPojo]
    apply(pojo)
  }

  private[transport] def apply(pojo: RequestMatcherPojo): RequestMatcher = {
    RequestMatcher(pojo.uri.map(Uri.apply), pojo.headers.map { case (k, v) ⇒
      k → TextMatcher(v)
    })
  }
}

private[transport] case class RequestMatcherPojo(uri: Option[UriPojo], headers: Map[String, TextMatcherPojo])
