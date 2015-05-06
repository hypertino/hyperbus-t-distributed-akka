package eu.inn.hyperbus.impl

import eu.inn.hyperbus.{HyperBusBase, HyperBus}
import eu.inn.hyperbus.protocol.{Body, DynamicBody, ErrorBody, Response}
import eu.inn.hyperbus.serialization.{ResponseBodyDecoder, ResponseDecoder, ResponseHeader, ResponseEncoder}
/*
object Helpers {
  def defaultResponseEncoder(hyperBus: HyperBusBase, response: Response[Body]): ResponseEncoder = {
    (response: Response[Body], outputStream: java.io.OutputStream) => {
      response.body match {
        case _: ErrorBody => eu.inn.hyperbus.serialization.createEncoder[Response[ErrorBody]](response.asInstanceOf[Response[ErrorBody]], outputStream)
        case _: DynamicBody => eu.inn.hyperbus.serialization.createEncoder[Response[DynamicBody]](response.asInstanceOf[Response[DynamicBody]], outputStream)
        case _ => hyperBus.lostResponse(response)
      }
    }
  }

  def defaultResponseDecoder(hyperBus: HyperBusBase, responseHeader: ResponseHeader): ResponseBodyDecoder = {
    (responseHeader:  ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser) => {
      val decoder = responseHeader.status match {
        case _ =>
          if (responseHeader.status >= 400 && responseHeader.status <= 599)
            eu.inn.hyperbus.serialization.createResponseBodyDecoder[ErrorBody]
          else
            eu.inn.hyperbus.serialization.createResponseBodyDecoder[DynamicBody]
      }
      decoder(responseHeader,responseBodyJson)
    }
  }
}
*/