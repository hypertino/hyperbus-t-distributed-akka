package eu.inn.hyperbus.impl

import eu.inn.hyperbus.model.{Body, Header, Response}
import eu.inn.hyperbus.serialization.{ResponseBodyDeserializer, ResponseHeader}
import eu.inn.hyperbus.transport.api.matchers.{AnyValue, SpecificValue, TransportRequestMatcher}
import eu.inn.hyperbus.transport.api.uri._

trait MacroApi {
  def responseDeserializer(responseHeader: ResponseHeader,
                           responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                           bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body]

  private def uriWithAnyValue(uriPattern: String): Uri = Uri(
    SpecificValue(uriPattern),
    UriParser.extractParameters(uriPattern).map(_ → AnyValue).toMap
  )

  def requestMatcher(uriPattern: String, method: String, contentType: Option[String]): TransportRequestMatcher = {
    TransportRequestMatcher(Some(uriWithAnyValue(uriPattern)), Map(Header.METHOD → SpecificValue(method)) ++
      contentType.map(c ⇒ Header.CONTENT_TYPE → SpecificValue(c))
    )
  }
}
