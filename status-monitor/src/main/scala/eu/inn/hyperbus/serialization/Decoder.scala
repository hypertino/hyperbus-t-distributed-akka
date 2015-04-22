package eu.inn.hyperbus.serialization


import com.fasterxml.jackson.core.JsonParser
import eu.inn.hyperbus.protocol.{Response, Body, Request}

case class RequestHeader(url:String, method:String, contentType:Option[String])

trait RequestDecoder {
  def decode(requestHeader: RequestHeader, jsonParser: JsonParser): Request[Body]
}

case class ResponseHeader(status:Int)

trait ResponseDecoder {
  def decode(responseHeader: ResponseHeader, jsonParser: JsonParser): Response[Body]
}

case class DecodeException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)