package eu.inn.hyperbus.rest

import java.io.{InputStream, OutputStream}

import com.fasterxml.jackson.core.JsonParser
import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.rest.standard._
import eu.inn.hyperbus.serialization.{DecodeException, RequestHeader}
import eu.inn.servicebus.transport.{SpecificValue, Filters, Topic}


trait DynamicBody extends Body with Links {
  def content: Value
  lazy val links: Body.LinksMap = content.__links[Option[Body.LinksMap]].getOrElse(Map.empty)

  def encode(outputStream: OutputStream): Unit = {
    import eu.inn.binders._
    import eu.inn.hyperbus.serialization.MessageEncoder.bindOptions
    eu.inn.binders.json.SerializerFactory.findFactory().withStreamGenerator(outputStream) { serializer=>
      serializer.bind[Value](content)
    }
  }
}

object DynamicBody {
  def apply(content: Value, contentType: Option[String]): DynamicBody = DynamicBodyContainer(content, contentType)

  def apply(content: Value): DynamicBody = DynamicBodyContainer(content, None)

  def decode(contentType: Option[String], jsonParser : com.fasterxml.jackson.core.JsonParser): DynamicBody = {
    import eu.inn.binders.json._
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      apply(deserializer.unbind[Value], contentType)
    }
  }
  def apply(contentType: Option[String], jsonParser : com.fasterxml.jackson.core.JsonParser): DynamicBody = decode(contentType, jsonParser)
  def unapply(dynamicBody: DynamicBody) = Some((dynamicBody.content, dynamicBody.contentType))
}

private [rest] case class DynamicBodyContainer(content: Value, contentType: Option[String] = None) extends DynamicBody

trait DynamicRequest extends Request[DynamicBody] {
  lazy val topic = Topic(url, Filters(UrlParser.extractParameters(url).map { arg ⇒
    arg → SpecificValue(
      body.content.asMap.get(arg).map(_.asString).getOrElse("") // todo: inner fields like abc.userId
    )
  }.toMap))
}

object DynamicRequest {
  def apply(requestHeader: RequestHeader, jsonParser: JsonParser): DynamicRequest = {
    val body = DynamicBody(requestHeader.contentType, jsonParser)
    val messageId = requestHeader.messageId
    val correlationId = requestHeader.correlationId.getOrElse(messageId)
    requestHeader.method match {
      case Method.GET => DynamicGet(requestHeader.url, body, messageId, correlationId)
      case Method.POST => DynamicPost(requestHeader.url, body, messageId, correlationId)
      case Method.PUT => DynamicPut(requestHeader.url, body, messageId, correlationId)
      case Method.DELETE => DynamicDelete(requestHeader.url, body, messageId, correlationId)
      case Method.PATCH => DynamicPatch(requestHeader.url, body, messageId, correlationId)
      case _ => throw new DecodeException(s"Unknown method: '${requestHeader.method}'") //todo: save more details (messageId) or introduce DynamicMethodRequest
    }
  }
}
