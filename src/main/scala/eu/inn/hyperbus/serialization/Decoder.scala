package eu.inn.hyperbus.serialization

import java.io.InputStream

case class RequestHeader(url:String, method:String, contentType:Option[String])

trait RequestDecoder[T] {
  def decode(header: RequestHeader, in: InputStream): T
}
