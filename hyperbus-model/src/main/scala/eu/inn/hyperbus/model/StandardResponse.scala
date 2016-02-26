package eu.inn.hyperbus.model

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
    responseHeader.status match {
      case Status.OK => Ok(body, responseHeader.headers)
      case Status.CREATED => Created(body.asInstanceOf[CreatedBody], responseHeader.headers)
      case Status.ACCEPTED => Accepted(body, responseHeader.headers)
      case Status.NON_AUTHORITATIVE_INFORMATION => NonAuthoritativeInformation(body, responseHeader.headers)
      case Status.NO_CONTENT => NoContent(body, responseHeader.headers)
      case Status.RESET_CONTENT => ResetContent(body, responseHeader.headers)
      case Status.PARTIAL_CONTENT => PartialContent(body, responseHeader.headers)
      case Status.MULTI_STATUS => MultiStatus(body, responseHeader.headers)

      case Status.MULTIPLE_CHOICES => MultipleChoices(body, responseHeader.headers)
      case Status.MOVED_PERMANENTLY => MovedPermanently(body, responseHeader.headers)
      case Status.FOUND => Found(body, responseHeader.headers)
      case Status.SEE_OTHER => SeeOther(body, responseHeader.headers)
      case Status.NOT_MODIFIED => NotModified(body, responseHeader.headers)
      case Status.USE_PROXY => UseProxy(body, responseHeader.headers)
      case Status.TEMPORARY_REDIRECT => TemporaryRedirect(body, responseHeader.headers)

      case Status.BAD_REQUEST => BadRequest(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.UNAUTHORIZED => Unauthorized(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.PAYMENT_REQUIRED => PaymentRequired(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.FORBIDDEN => Forbidden(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.NOT_FOUND => NotFound(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.METHOD_NOT_ALLOWED => MethodNotAllowed(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.NOT_ACCEPTABLE => NotAcceptable(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.PROXY_AUTHENTICATION_REQUIRED => ProxyAuthenticationRequired(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.REQUEST_TIMEOUT => RequestTimeout(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.CONFLICT => Conflict(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.GONE => Gone(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.LENGTH_REQUIRED => LengthRequired(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.PRECONDITION_FAILED => PreconditionFailed(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.REQUEST_ENTITY_TOO_LARGE => RequestEntityTooLarge(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.REQUEST_URI_TOO_LONG => RequestUriTooLong(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.UNSUPPORTED_MEDIA_TYPE => UnsupportedMediaType(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.REQUESTED_RANGE_NOT_SATISFIABLE => RequestedRangeNotSatisfiable(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.EXPECTATION_FAILED => ExpectationFailed(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.UNPROCESSABLE_ENTITY => UnprocessableEntity(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.LOCKED => Locked(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.FAILED_DEPENDENCY => FailedDependency(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.TOO_MANY_REQUEST => TooManyRequest(body.asInstanceOf[ErrorBody], responseHeader.headers)

      case Status.INTERNAL_SERVER_ERROR => InternalServerError(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.NOT_IMPLEMENTED => NotImplemented(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.BAD_GATEWAY => BadGateway(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.SERVICE_UNAVAILABLE => ServiceUnavailable(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.GATEWAY_TIMEOUT => GatewayTimeout(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.HTTP_VERSION_NOT_SUPPORTED => HttpVersionNotSupported(body.asInstanceOf[ErrorBody], responseHeader.headers)
      case Status.INSUFFICIENT_STORAGE => InsufficientStorage(body.asInstanceOf[ErrorBody], responseHeader.headers)
    }
  }
}
