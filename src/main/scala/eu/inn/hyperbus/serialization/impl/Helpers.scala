package eu.inn.hyperbus.serialization.impl

import eu.inn.hyperbus.protocol.{Post, Response, Body, Request}
import eu.inn.servicebus.serialization.Decoder

object Helpers {

  case class RequestHeader(url:String, method:String, contentType:Option[String])
  case class ResponseHeader(status:Int)
  
  def toJson[B <: Body](request: Request[B], bodyJson: String) = {
    import eu.inn.binders.json._
    val req = RequestHeader(request.url, request.method, request.body.contentType)
    s"""{"request":${req.toJson},"body":$bodyJson"}"""
  }

  def toJson[B <: Body](response: Response[B], bodyJson: String) = {
    import eu.inn.binders.json._
    val resp = ResponseHeader(response.status)
    s"""{"request":${resp.toJson},"body":$bodyJson"}"""
  }

  def parseJson[B <: Body](jsonString: String, bodyDecoder: Decoder[B]) = {
    new Post(bodyDecoder.decode(jsonString)) {
      def url = ""
    }
  }
}
