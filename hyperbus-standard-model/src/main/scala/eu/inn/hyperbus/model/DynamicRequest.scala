package eu.inn.hyperbus.model

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}

import com.fasterxml.jackson.core.JsonParser
import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.{MessageDeserializer, DecodeException, RequestHeader}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.uri.Uri


trait DynamicBody extends Body with Links {
  def content: Value

  lazy val links: LinksMap.LinksMapType = content.__links[Option[LinksMap.LinksMapType]].getOrElse(Map.empty)

  def serialize(outputStream: OutputStream): Unit = {
    import eu.inn.binders._
    import eu.inn.hyperbus.serialization.MessageSerializer.bindOptions
    eu.inn.binders.json.SerializerFactory.findFactory().withStreamGenerator(outputStream) { serializer =>
      serializer.bind[Value](content)
    }
  }

  def copy(
            contentType: Option[String] = this.contentType,
            content: Value = this.content
          ): DynamicBody = {
    DynamicBody(contentType, content)
  }
}

object DynamicBody {
  def apply(contentType: Option[String], content: Value): DynamicBody = DynamicBodyContainer(contentType, content)

  def apply(content: Value): DynamicBody = DynamicBodyContainer(None, content)

  def deserialize(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): DynamicBody = {
    import eu.inn.binders.json._
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      apply(contentType, deserializer.unbind[Value])
    }
  }

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): DynamicBody = deserialize(contentType, jsonParser)

  def unapply(dynamicBody: DynamicBody) = Some((dynamicBody.contentType, dynamicBody.content))
}

private[model] case class DynamicBodyContainer(contentType: Option[String], content: Value) extends DynamicBody

trait DynamicRequest extends Request[DynamicBody] {
  override def toString = s"DynamicRequest(RequestHeader($uri,$method,${body.contentType},$messageId,$correlationId),$body)"

  def copy(
            uri: Uri = this.uri,
            method: String = this.method,
            messageId: String = this.messageId,
            correlationId: String = this.correlationId,
            headers: Map[String, Seq[String]] = this.headers,
            body: DynamicBody = this.body
          ): DynamicRequest = {
    DynamicRequest(
      RequestHeader(uri, method, body.contentType, messageId,
        if (messageId == correlationId) None else Some(correlationId),
        headers), body
    )
  }
}

object DynamicRequest {
  def apply(requestHeader: RequestHeader, jsonParser: JsonParser): DynamicRequest = {
    val b = DynamicBody(requestHeader.contentType, jsonParser)
    apply(requestHeader, b)
  }

  def apply(message: String, encoding: String = "UTF-8"): DynamicRequest = {
    apply(new ByteArrayInputStream(message.getBytes(encoding)))
  }

  def apply(inputStream: InputStream): DynamicRequest = {
    MessageDeserializer.deserializeRequestWith(inputStream) { (requestHeader, jsonParser) ⇒
      DynamicRequest(requestHeader, jsonParser)
    }
  }

  def apply(requestHeader: RequestHeader, body: DynamicBody): DynamicRequest = {
    val msgId = requestHeader.messageId
    val cId = requestHeader.correlationId.getOrElse(msgId)
    val h = requestHeader.headers
    val b = body
    requestHeader.method match {
      case Method.GET => DynamicGet(requestHeader.uri, b, h, msgId, cId)
      case Method.POST => DynamicPost(requestHeader.uri, b, h,msgId, cId)
      case Method.PUT => DynamicPut(requestHeader.uri, b, h,msgId, cId)
      case Method.DELETE => DynamicDelete(requestHeader.uri, b, h,msgId, cId)
      case Method.PATCH => DynamicPatch(requestHeader.uri, b, h, msgId, cId)
      case other ⇒ new DynamicRequest {
        override def uri: Uri = requestHeader.uri
        override def method: String = requestHeader.method
        override def correlationId: String = cId
        override def messageId: String = msgId
        override def body: DynamicBody = b
        override def headers: Map[String,Seq[String]] = h
      }
      //case _ => throw new DecodeException(s"Unknown method: '${requestHeader.method}'") //todo: save more details (messageId) or introduce DynamicMethodRequest
    }
  }

  def unapply(request: DynamicRequest): Option[(RequestHeader, DynamicBody)] = Some((
    RequestHeader(request.uri, request.method, request.body.contentType, request.messageId,
      if (request.messageId == request.correlationId) None else Some(request.correlationId),
      request.headers),
    request.body
    ))
}
