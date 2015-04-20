package eu.inn.hyperbus.serialization

import java.io.InputStream

import eu.inn.hyperbus.protocol.{Body, Request}

case class RequestHeader(url:String, method:String, contentType:Option[String])

trait RequestDecoder[R <: Request[Body]] {
  def decode(requestHeader: RequestHeader, inputStream: InputStream)
}

case class ResponseHeader(status:Int)

case class DecodeException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)