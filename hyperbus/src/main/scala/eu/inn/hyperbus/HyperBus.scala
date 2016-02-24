package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.impl.MacroApi
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.{TransportRequestMatcher, TextMatcher}
import eu.inn.hyperbus.util.{LogUtils, Subscriptions}
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.util.Try
import scala.util.control.NonFatal

class HyperBus(val transportManager: TransportManager,
               val defaultGroupName: Option[String] = None,
               val logMessages: Boolean = true)(implicit val executionContext: ExecutionContext)
  extends HyperBusApi {
  import LogUtils._
  protected val log = LoggerFactory.getLogger(this.getClass)

  def onEvent(requestMatcher: TransportRequestMatcher, groupName: Option[String])
             (handler: (DynamicRequest) => Future[Unit]): String = {
    onEvent[DynamicRequest](requestMatcher, groupName, DynamicRequest.apply)(handler)
  }

  def onCommand(requestMatcher: TransportRequestMatcher)
               (handler: DynamicRequest => Future[_ <: Response[Body]]): String = {
    onCommand[Response[Body], DynamicRequest](requestMatcher, DynamicRequest.apply)(handler)
  }


  protected class RequestReplySubscription[REQ <: Request[Body]](
                                                                       handler: (REQ) => Future[Response[Body]],
                                                                       requestDeserializer: RequestDeserializer[REQ]
                                                                     ){
    def underlyingHandler(in: REQ): Future[Response[Body]] = {
      if (logMessages && log.isTraceEnabled) {
        log.trace(Map("messageId" → in.messageId,"correlationId" → in.correlationId, "subscriptionId" → this.hashCode.toHexString,
          s"hyperBus ~> $in")
      }
      val futureResult = handler(in)
      futureResult.recoverWith {
        case z: Response[_] ⇒ Future.successful(z)
        case t: Throwable ⇒ Future.successful(unhandledException(in, t))
      }
    }

    def deserializer(inputStream: InputStream): REQ = {
      MessageDeserializer.deserializeRequestWith[REQ](inputStream) { (requestHeader, requestBodyJson) =>
        requestDeserializer(requestHeader, requestBodyJson)
      }
    }
  }

  protected class PubSubSubscription[REQ <: Request[Body]](
                                                                 handler: (REQ) => Future[Unit],
                                                                 requestDeserializer: RequestDeserializer[REQ]
                                                               ) {
    def underlyingHandler(in: REQ): Future[Unit] = {
      val futureResult = handler(in)
      futureResult.recoverWith {
        case z: Response[_] ⇒ Future.successful(z)
        case t: Throwable ⇒ Future.successful(unhandledException(in, t))
      }
    }

    def deserializer(inputStream: InputStream): REQ = {
      MessageDeserializer.deserializeRequestWith[REQ](inputStream) { (requestHeader, requestBodyJson) =>
        requestDeserializer(requestHeader, requestBodyJson)
      }
    }
  }

  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        responseDeserializer: ResponseDeserializer[RESP]): Future[RESP] = {

    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → request.messageId,"correlationId" → request.correlationId), s"hyperBus <~ $request")
    }
    val outputDeserializer = MessageDeserializer.deserializeResponseWith(_: InputStream)(responseDeserializer)
    transportManager.ask[RESP](request, outputDeserializer) map {
      case throwable: Throwable ⇒
        throw throwable
      case other ⇒
        if (logMessages && log.isTraceEnabled) {
          log.trace(Map("messageId" → other.messageId,"correlationId" → other.correlationId), s"hyperBus ~(R)~> $other")
        }
        other
    }
  }

  def publish[REQ <: Request[Body]](request: REQ): Future[PublishResult] = {
    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → request.messageId,"correlationId" → request.correlationId), s"hyperBus <| $request")
    }
    transportManager.publish(request)
  }

  def onCommand[RESP <: Response[Body], REQ <: Request[Body]](requestMatcher: TransportRequestMatcher,
                                                              requestDeserializer: RequestDeserializer[REQ])
                                                             (handler: (REQ) => Future[RESP]): String = {

    //lookup
    val subscription = new RequestReplySubscription[REQ](handler, requestDeserializer)
    transportManager.onCommand(requestMatcher, subscription.deserializer)(subscription.underlyingHandler)
  }

  def onEvent[REQ <: Request[Body]](requestMatcher: TransportRequestMatcher,
                                    groupName: Option[String],
                                    requestDeserializer: RequestDeserializer[REQ])
                                   (handler: (REQ) => Future[Unit]): String = {

    val finalGroupName = groupName.getOrElse {
      defaultGroupName.getOrElse {
        throw new UnsupportedOperationException(s"Can't subscribe: group name is not defined")
      }
    }
    val subscription = new PubSubSubscription[REQ](handler, requestDeserializer)
    transportManager.onEvent(requestMatcher, finalGroupName, subscription.deserializer)(subscription.underlyingHandler)
  }

  def off(subscriptionId: String): Unit = {
    transportManager.off(subscriptionId)
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    transportManager.shutdown(duration)
  }

  protected def exceptionSerializer(exception: Throwable, outputStream: java.io.OutputStream): Unit = {
    exception match {
      case r: ErrorResponse ⇒ r.serialize(outputStream)
      case t ⇒
        val error = InternalServerError(ErrorBody(DefError.INTERNAL_ERROR, Some(t.getMessage)))
        logError("Unhandled exception", error, t)
        error.serialize(outputStream)
    }
  }

  val macroApiImpl = new MacroApi {
    def responseDeserializer(responseHeader: ResponseHeader,
                             responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                             bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body] = {
      StandardResponse(responseHeader, responseBodyJson, bodyDeserializer)
    }
  }

  protected def logError(msg: String, error: HyperBusException[ErrorBody], throwable: Throwable): Unit = {
    log.error(msg + ". #" + error.body.errorId, throwable)
  }

  protected def safeErrorMessage(msg: String, request: Request[Body]): String = {
    msg + " " + safe(() => request.uri.toString) + safe(() => request.headers.toString)
  }

  /*
  protected def unhandledRequest(routeKey: String, request: Request[Body]): Response[Body] = {
    val s = safeErrorMessage("Unhandled request", request)
    InternalServerError(ErrorBody(DefError.HANDLER_NOT_FOUND, Some(s)))
  }

  protected def unhandledPublication(routeKey: String, request: Request[Body]): Unit = {
    log.warn(safeErrorMessage("Unhandled publication", request, routeKey))
  }*/

  protected def unhandledException(request: Request[Body], exception: Throwable): Response[Body] = {
    val errorBody = ErrorBody(DefError.INTERNAL_ERROR, Some(
      safeErrorMessage(s"Unhandled exception: ${exception.getMessage}", request)
    ))
    log.error(errorBody.message + ". #" + errorBody.errorId, exception)
    InternalServerError(errorBody)
  }

  protected def responseSerializerNotFound(response: Response[Body]) = log.error("Can't serialize response: {}", response)

  protected def getRouteKey(requestMatcher: TransportRequestMatcher, groupName: Option[String]) = {
    if (requestMatcher.uri.isEmpty)
      throw new IllegalArgumentException(s"uri is not set on matcher: $requestMatcher")
    val specificUri = requestMatcher.uri.get.pattern.specific // todo: implement other filters?

    groupName.map {
      specificUri + "#" + _
    } getOrElse specificUri
  }

  protected def safe(t: () => String): String = Try(t()).getOrElse("???")
}
