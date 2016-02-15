package eu.inn.hyperbus.model.standard

import eu.inn.hyperbus.model.{Body, Response}
import eu.inn.hyperbus.serialization._

object StandardResponse {

  def apply(responseHeader: ResponseHeader,
                           responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                           bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body] = {
    val body =
      if (bodyDeserializer.isDefinedAt(responseHeader))
        bodyDeserializer(responseHeader)(responseHeader.contentType, responseBodyJson)
      else
        StandardResponseBody(responseHeader, responseBodyJson)
    apply(responseHeader, body)
  }

  def apply(responseHeader: ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser): Response[Body] = {
    apply(responseHeader, responseBodyJson, PartialFunction.empty)
  }

  def apply(responseHeader: ResponseHeader, body: Body): Response[Body] = {
    val messageId = responseHeader.messageId
    val correlationId = responseHeader.correlationId.getOrElse(messageId)
    responseHeader.status match {
      case Status.OK => Ok(body, responseHeader.headers, messageId, correlationId)
      case Status.CREATED => Created(body.asInstanceOf[CreatedBody], responseHeader.headers, messageId, correlationId)
      case Status.ACCEPTED => Accepted(body, responseHeader.headers, messageId, correlationId)
      case Status.NON_AUTHORITATIVE_INFORMATION => NonAuthoritativeInformation(body, responseHeader.headers, messageId, correlationId)
      case Status.NO_CONTENT => NoContent(body, responseHeader.headers, messageId, correlationId)
      case Status.RESET_CONTENT => ResetContent(body, responseHeader.headers, messageId, correlationId)
      case Status.PARTIAL_CONTENT => PartialContent(body, responseHeader.headers, messageId, correlationId)
      case Status.MULTI_STATUS => MultiStatus(body, responseHeader.headers, messageId, correlationId)

      case Status.MULTIPLE_CHOICES => MultipleChoices(body, responseHeader.headers, messageId, correlationId)
      case Status.MOVED_PERMANENTLY => MovedPermanently(body, responseHeader.headers, messageId, correlationId)
      case Status.FOUND => Found(body, responseHeader.headers, messageId, correlationId)
      case Status.SEE_OTHER => SeeOther(body, responseHeader.headers, messageId, correlationId)
      case Status.NOT_MODIFIED => NotModified(body, responseHeader.headers, messageId, correlationId)
      case Status.USE_PROXY => UseProxy(body, responseHeader.headers, messageId, correlationId)
      case Status.TEMPORARY_REDIRECT => TemporaryRedirect(body, responseHeader.headers, messageId, correlationId)

      case Status.BAD_REQUEST => BadRequest(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.UNAUTHORIZED => Unauthorized(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.PAYMENT_REQUIRED => PaymentRequired(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.FORBIDDEN => Forbidden(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.NOT_FOUND => NotFound(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.METHOD_NOT_ALLOWED => MethodNotAllowed(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.NOT_ACCEPTABLE => NotAcceptable(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.PROXY_AUTHENTICATION_REQUIRED => ProxyAuthenticationRequired(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.REQUEST_TIMEOUT => RequestTimeout(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.CONFLICT => Conflict(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.GONE => Gone(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.LENGTH_REQUIRED => LengthRequired(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.PRECONDITION_FAILED => PreconditionFailed(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.REQUEST_ENTITY_TOO_LARGE => RequestEntityTooLarge(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.REQUEST_URI_TOO_LONG => RequestUriTooLong(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.UNSUPPORTED_MEDIA_TYPE => UnsupportedMediaType(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.REQUESTED_RANGE_NOT_SATISFIABLE => RequestedRangeNotSatisfiable(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.EXPECTATION_FAILED => ExpectationFailed(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.UNPROCESSABLE_ENTITY => UnprocessableEntity(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.LOCKED => Locked(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.FAILED_DEPENDENCY => FailedDependency(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.TOO_MANY_REQUEST => TooManyRequest(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)

      case Status.INTERNAL_SERVER_ERROR => InternalServerError(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.NOT_IMPLEMENTED => NotImplemented(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.BAD_GATEWAY => BadGateway(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.SERVICE_UNAVAILABLE => ServiceUnavailable(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.GATEWAY_TIMEOUT => GatewayTimeout(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.HTTP_VERSION_NOT_SUPPORTED => HttpVersionNotSupported(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
      case Status.INSUFFICIENT_STORAGE => InsufficientStorage(body.asInstanceOf[ErrorBody], responseHeader.headers, messageId, correlationId)
    }
  }
}
