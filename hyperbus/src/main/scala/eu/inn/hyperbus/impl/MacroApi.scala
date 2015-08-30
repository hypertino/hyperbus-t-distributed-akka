package eu.inn.hyperbus.impl

import eu.inn.hyperbus.rest.{Body, Response, UrlParser}
import eu.inn.hyperbus.serialization.{ResponseBodyDecoder, ResponseHeader}
import eu.inn.servicebus.transport.{AnyValue, Filters, Topic}

trait MacroApi {
  def responseDecoder(responseHeader: ResponseHeader,
                      responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                      bodyDecoder: PartialFunction[ResponseHeader, ResponseBodyDecoder]): Response[Body]

  def topicWithAnyValue(url: String): Topic = Topic(url, Filters(UrlParser.extractParameters(url).map(_ → AnyValue).toMap))
}
