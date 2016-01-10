package eu.inn.hyperbus.impl

import eu.inn.hyperbus.model.{UriParser, Body, Response, UriParser$}
import eu.inn.hyperbus.serialization.{ResponseBodyDeserializer, ResponseHeader}
import eu.inn.hyperbus.transport.api._

trait MacroApi {
  def responseDeserializer(responseHeader: ResponseHeader,
                           responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                           bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body]

  def uriWithAnyValue(uriPattern: String): Uri = Uri(uriPattern, UriParts(UriParser.extractParameters(uriPattern).map(_ → AnyValue).toMap))
}
