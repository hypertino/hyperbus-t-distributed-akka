package eu.inn.hyperbus


import com.fasterxml.jackson.core.JsonParser
import eu.inn.binders.dynamic.Value
import eu.inn.binders.json.SerializerFactory
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.standard._
import eu.inn.hyperbus.serialization.{DecodeException, RequestHeader, ResponseHeader}

private[hyperbus] object HyperBusUtils {
  // todo: Generic Errors and Responses
  // handle non-standrard status
  def createResponse(responseHeader: ResponseHeader, body: Body): Response[Body] = {
    responseHeader.status match {
      case Status.OK => Ok(body)
      case Status.CREATED => Created(body.asInstanceOf[CreatedBody])
      case Status.ACCEPTED => Accepted(body)
      case Status.NON_AUTHORITATIVE_INFORMATION => NonAuthoritativeInformation(body)
      case Status.NO_CONTENT => NoContent(body)
      case Status.RESET_CONTENT => ResetContent(body)
      case Status.PARTIAL_CONTENT => PartialContent(body)
      case Status.MULTI_STATUS => MultiStatus(body)

      case Status.MULTIPLE_CHOICES => MultipleChoices(body)
      case Status.MOVED_PERMANENTLY => MovedPermanently(body)
      case Status.FOUND => Found(body)
      case Status.SEE_OTHER => SeeOther(body)
      case Status.NOT_MODIFIED => NotModified(body)
      case Status.USE_PROXY => UseProxy(body)
      case Status.TEMPORARY_REDIRECT => TemporaryRedirect(body)

      case Status.BAD_REQUEST => BadRequest(body.asInstanceOf[ErrorBodyTrait])
      case Status.UNAUTHORIZED => Unauthorized(body.asInstanceOf[ErrorBodyTrait])
      case Status.PAYMENT_REQUIRED => PaymentRequired(body.asInstanceOf[ErrorBodyTrait])
      case Status.FORBIDDEN => Forbidden(body.asInstanceOf[ErrorBodyTrait])
      case Status.NOT_FOUND => NotFound(body.asInstanceOf[ErrorBodyTrait])
      case Status.METHOD_NOT_ALLOWED => MethodNotAllowed(body.asInstanceOf[ErrorBodyTrait])
      case Status.NOT_ACCEPTABLE => NotAcceptable(body.asInstanceOf[ErrorBodyTrait])
      case Status.PROXY_AUTHENTICATION_REQUIRED => ProxyAuthenticationRequired(body.asInstanceOf[ErrorBodyTrait])
      case Status.REQUEST_TIMEOUT => RequestTimeout(body.asInstanceOf[ErrorBodyTrait])
      case Status.CONFLICT => Conflict(body.asInstanceOf[ErrorBodyTrait])
      case Status.GONE => Gone(body.asInstanceOf[ErrorBodyTrait])
      case Status.LENGTH_REQUIRED => LengthRequired(body.asInstanceOf[ErrorBodyTrait])
      case Status.PRECONDITION_FAILED => PreconditionFailed(body.asInstanceOf[ErrorBodyTrait])
      case Status.REQUEST_ENTITY_TOO_LARGE => RequestEntityTooLarge(body.asInstanceOf[ErrorBodyTrait])
      case Status.REQUEST_URI_TOO_LONG => RequestUriTooLong(body.asInstanceOf[ErrorBodyTrait])
      case Status.UNSUPPORTED_MEDIA_TYPE => UnsupportedMediaType(body.asInstanceOf[ErrorBodyTrait])
      case Status.REQUESTED_RANGE_NOT_SATISFIABLE => RequestedRangeNotSatisfiable(body.asInstanceOf[ErrorBodyTrait])
      case Status.EXPECTATION_FAILED => ExpectationFailed(body.asInstanceOf[ErrorBodyTrait])
      case Status.UNPROCESSABLE_ENTITY => UnprocessableEntity(body.asInstanceOf[ErrorBodyTrait])
      case Status.LOCKED => Locked(body.asInstanceOf[ErrorBodyTrait])
      case Status.FAILED_DEPENDENCY => FailedDependency(body.asInstanceOf[ErrorBodyTrait])
      case Status.TOO_MANY_REQUEST => TooManyRequest(body.asInstanceOf[ErrorBodyTrait])

      case Status.INTERNAL_SERVER_ERROR => InternalServerError(body.asInstanceOf[ErrorBodyTrait])
      case Status.NOT_IMPLEMENTED => NotImplemented(body.asInstanceOf[ErrorBodyTrait])
      case Status.BAD_GATEWAY => BadGateway(body.asInstanceOf[ErrorBodyTrait])
      case Status.SERVICE_UNAVAILABLE => ServiceUnavailable(body.asInstanceOf[ErrorBodyTrait])
      case Status.GATEWAY_TIMEOUT => GatewayTimeout(body.asInstanceOf[ErrorBodyTrait])
      case Status.HTTP_VERSION_NOT_SUPPORTED => HttpVersionNotSupported(body.asInstanceOf[ErrorBodyTrait])
      case Status.INSUFFICIENT_STORAGE => InsufficientStorage(body.asInstanceOf[ErrorBodyTrait])
    }
  }

  def decodeDynamicRequest(requestHeader: RequestHeader, jsonParser: JsonParser): Request[Body] = {
    val body = SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      DynamicBody(deserializer.unbind[Value], requestHeader.contentType)
    }
    requestHeader.method match {
      case Method.GET => DynamicGet(requestHeader.url, body)
      case Method.POST => DynamicPost(requestHeader.url, body)
      case Method.PUT => DynamicPut(requestHeader.url, body)
      case Method.DELETE => DynamicDelete(requestHeader.url, body)
      case Method.PATCH => DynamicPatch(requestHeader.url, body)
      case _ => throw new DecodeException(s"Unknown method: '${requestHeader.method}'") //todo: save more details (messageId) or introduce DynamicMethodRequest
    }
  }
}
