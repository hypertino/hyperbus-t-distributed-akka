package eu.inn.hyperbus

import com.fasterxml.jackson.core.JsonParser
import eu.inn.binders.dynamic.Value
import eu.inn.binders.json.SerializerFactory
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.serialization.{DecodeException, RequestHeader, ResponseHeader}
import eu.inn.servicebus.transport.{Topic, AnyValue, PartitionArgs}

import scala.collection.mutable

private [hyperbus] object HyperBusUtils {
  // todo: Generic Errors and Responses
  // todo: all responses
  def createResponse(responseHeader: ResponseHeader, body: Body): Response[Body] = {
    responseHeader.status match {
      case Status.OK => Ok(body)
      case Status.CREATED => Created(body.asInstanceOf[CreatedBody])
      case Status.CONFLICT => Conflict(body.asInstanceOf[ErrorBodyTrait])
      case Status.INTERNAL_ERROR => InternalError(body.asInstanceOf[ErrorBodyTrait])
    }
  }

  def decodeDynamicRequest(requestHeader: RequestHeader, jsonParser: JsonParser): Request[Body] = {
    val body = SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      DefaultDynamicBody(deserializer.unbind[Value], requestHeader.contentType)
    }
    requestHeader.method match {
      case StandardMethods.GET => DynamicGet(requestHeader.url, body)
      case StandardMethods.POST => DynamicPost(requestHeader.url, body)
      case StandardMethods.PUT => DynamicPut(requestHeader.url, body)
      case StandardMethods.DELETE => DynamicDelete(requestHeader.url, body)
      case StandardMethods.PATCH => DynamicPatch(requestHeader.url, body)
      case _ => throw new DecodeException(s"Unknown method: '${requestHeader.method}'") //todo: save more details (messageId) or introduce DynamicMethodRequest
    }
  }
}
