package eu.inn.hyperbus.impl

import eu.inn.hyperbus.model.{Body, Response, UrlParser}
import eu.inn.hyperbus.serialization.{ResponseBodyDeserializer, ResponseHeader}
import eu.inn.hyperbus.transport.api.{AnyValue, Filters, Topic}

trait MacroApi {
  def responseDeserializer(responseHeader: ResponseHeader,
                      responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                      bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body]

  def topicWithAnyValue(url: String): Topic = Topic(url, Filters(UrlParser.extractParameters(url).map(_ → AnyValue).toMap))
}
