package eu.inn.hyperbus.impl

import eu.inn.hyperbus.model.{Body, Response}
import eu.inn.hyperbus.serialization.{ResponseBodyDeserializer, ResponseHeader}
import eu.inn.hyperbus.transport.api.matchers.{AnyValue, SpecificValue}
import eu.inn.hyperbus.transport.api.uri._

trait MacroApi {
  def responseDeserializer(responseHeader: ResponseHeader,
                           responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                           bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body]

  def uriWithAnyValue(uriPattern: String): Uri = Uri(
    SpecificValue(uriPattern),
    UriParser.extractParameters(uriPattern).map(_ → AnyValue).toMap
  )
}
