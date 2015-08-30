package eu.inn.hyperbus.impl

import eu.inn.hyperbus.rest.{Body, Response}
import eu.inn.hyperbus.serialization.{ResponseBodyDecoder, ResponseHeader}
import eu.inn.servicebus.serialization.Encoder

trait MacroApi {
  def responseDecoder(responseHeader: ResponseHeader,
                      responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                      bodyDecoder: PartialFunction[ResponseHeader, ResponseBodyDecoder]): Response[Body]
}
