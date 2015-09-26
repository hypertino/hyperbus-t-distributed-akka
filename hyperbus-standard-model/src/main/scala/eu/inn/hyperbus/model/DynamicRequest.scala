package eu.inn.hyperbus.model

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}

import com.fasterxml.jackson.core.JsonParser
import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.{MessageDeserializer, DecodeException, RequestHeader}
import eu.inn.hyperbus.transport.api.{Filters, SpecificValue, Topic}


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
  lazy val topic = Topic(url, Filters(UrlParser.extractParameters(url).map { arg ⇒
    arg → SpecificValue(
      body.content.asMap.get(arg).map(_.asString).getOrElse("") // todo: inner fields like abc.userId
    )
  }.toMap))

  override def toString = s"DynamicRequest(RequestHeader($url,$method,${body.contentType},$messageId,$correlationId),$body)"
}

object DynamicRequest {
  def apply(requestHeader: RequestHeader, jsonParser: JsonParser): DynamicRequest = deserialize(requestHeader, jsonParser)

  def apply(message: String): DynamicRequest = {
    apply(new ByteArrayInputStream(message.getBytes("UTF-8")))
  }

  def apply(inputStream: InputStream): DynamicRequest = {
    MessageDeserializer.deserializeRequestWith(inputStream) { (requestHeader, jsonParser) ⇒
      DynamicRequest(requestHeader, jsonParser)
    }
  }

  def unapply(request: DynamicRequest): Option[(RequestHeader, DynamicBody)] = Some((
    RequestHeader(request.url, request.method, request.body.contentType, request.messageId,
      if (request.messageId == request.correlationId) None else Some(request.correlationId)),
    request.body
    ))

  def deserialize(requestHeader: RequestHeader, jsonParser: JsonParser): DynamicRequest = {
    val b = DynamicBody(requestHeader.contentType, jsonParser)
    val msgId = requestHeader.messageId
    val cId = requestHeader.correlationId.getOrElse(msgId)
    requestHeader.method match {
      case Method.GET => DynamicGet(requestHeader.url, b, msgId, cId)
      case Method.POST => DynamicPost(requestHeader.url, b, msgId, cId)
      case Method.PUT => DynamicPut(requestHeader.url, b, msgId, cId)
      case Method.DELETE => DynamicDelete(requestHeader.url, b, msgId, cId)
      case Method.PATCH => DynamicPatch(requestHeader.url, b, msgId, cId)
      case other ⇒ new DynamicRequest {
        override def url: String = requestHeader.url
        override def method: String = requestHeader.method
        override def correlationId: String = cId
        override def messageId: String = msgId
        override def body: DynamicBody = b
      }
      //case _ => throw new DecodeException(s"Unknown method: '${requestHeader.method}'") //todo: save more details (messageId) or introduce DynamicMethodRequest
    }
  }
}
