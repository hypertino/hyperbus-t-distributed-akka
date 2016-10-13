package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.impl.MacroApi
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.util.LogUtils
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable

import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.experimental.macros
import scala.util.Try

class Hyperbus(val transportManager: TransportManager,
               val defaultGroupName: Option[String] = None,
               val logMessages: Boolean = true)(implicit val executionContext: ExecutionContext)
  extends HyperbusApi {

  import LogUtils._

  protected val log = LoggerFactory.getLogger(this.getClass)

  def onEvent(requestMatcher: RequestMatcher, groupName: Option[String]): Observable[DynamicRequest] = {
    onEvent[DynamicRequest](requestMatcher, groupName, DynamicRequest.apply)
  }

  def onCommand(requestMatcher: RequestMatcher)
               (handler: DynamicRequest => Future[_ <: Response[Body]]): Future[Subscription] = {
    onCommand[Response[Body], DynamicRequest](requestMatcher, DynamicRequest.apply)(handler)
  }


  protected class RequestReplySubscription[REQ <: Request[Body]](val handler: (REQ) => Future[Response[Body]],
                                                                 val requestDeserializer: RequestDeserializer[REQ]) {
    def underlyingHandler(in: TransportRequest): Future[TransportResponse] = {
      if (logMessages && log.isTraceEnabled) {
        log.trace(Map("messageId" → in.messageId, "correlationId" → in.correlationId,
          "subscriptionId" → this.hashCode.toHexString), s"hyperbus ~> $in")
      }
      val futureOut = handler(in.asInstanceOf[REQ]) recover {
        case z: Response[_] ⇒ z
        case t: Throwable ⇒ unhandledException(in.asInstanceOf[REQ], t)
      }
      if (logMessages && log.isTraceEnabled) {
        futureOut map { out ⇒
          log.trace(Map("messageId" → out.messageId, "correlationId" → out.correlationId,
            "subscriptionId" → this.hashCode.toHexString), s"hyperbus <~(R)~  $out")
          out
        }
      } else {
        futureOut
      }
    }
  }
//
//  protected class PubSubSubscription[REQ <: Request[Body]](val requestDeserializer: RequestDeserializer[REQ]) {
//    def underlyingHandler(in: TransportRequest): Unit = {
//      if (logMessages && log.isTraceEnabled) {
//        log.trace(Map("messageId" → in.messageId, "correlationId" → in.correlationId,
//          "subscriptionId" → this.hashCode.toHexString), s"hyperbus |> $in")
//      }
//    }
//  }

  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        responseDeserializer: ResponseDeserializer[RESP]): Future[RESP] = {

    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → request.messageId, "correlationId" → request.correlationId), s"hyperbus <~ $request")
    }
    val outputDeserializer = MessageDeserializer.deserializeResponseWith(_: InputStream)(responseDeserializer)
    transportManager.ask(request, outputDeserializer) map { r ⇒
      (r: @unchecked) match {
        case throwable: Throwable ⇒
          throw throwable
        case other ⇒
          if (logMessages && log.isTraceEnabled) {
            log.trace(Map("messageId" → other.messageId, "correlationId" → other.correlationId), s"hyperbus ~(R)~> $other")
          }
          other.asInstanceOf[RESP]
      }
    }
  }

  def publish[REQ <: Request[Body]](request: REQ): Future[PublishResult] = {
    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → request.messageId, "correlationId" → request.correlationId), s"hyperbus <| $request")
    }
    transportManager.publish(request)
  }

  def onCommand[RESP <: Response[Body], REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                                              requestDeserializer: RequestDeserializer[REQ])
                                                             (handler: (REQ) => Future[RESP]): Future[Subscription] = {

    val subscription = new RequestReplySubscription[REQ](handler, requestDeserializer)
    transportManager.onCommand(requestMatcher, subscription.requestDeserializer)(subscription.underlyingHandler)
  }

  def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                    groupName: Option[String],
                                    requestDeserializer: RequestDeserializer[REQ]): Observable[REQ] = {
    Observable { subscriber ⇒
      val finalGroupName = groupName.getOrElse {
        defaultGroupName.getOrElse {
          throw new UnsupportedOperationException(s"Can't subscribe: group name is not defined")
        }
      }
      val subscription = Await.result(transportManager.onEvent(requestMatcher, finalGroupName, requestDeserializer, subscriber), 20 seconds)
      subscriber.add(rx.lang.scala.Subscription {
        off(subscription)
      })
    }
  }

  def off(subscription: Subscription): Future[Unit] = {
    transportManager.off(subscription)
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

  protected def logError(msg: String, error: HyperbusException[ErrorBody], throwable: Throwable): Unit = {
    log.error(msg + ". #" + error.body.errorId, throwable)
  }

  protected def safeErrorMessage(msg: String, request: Request[Body]): String = {
    msg + " " + safe(() => request.uri.toString) + safe(() => request.headers.toString)
  }

  protected def unhandledException(request: Request[Body], exception: Throwable): Response[Body] = {
    val errorBody = ErrorBody(DefError.INTERNAL_ERROR, Some(
      safeErrorMessage(s"Unhandled exception: ${exception.getMessage}", request)
    ))
    log.error(errorBody.message + ". #" + errorBody.errorId, exception)
    InternalServerError(errorBody)
  }

  protected def responseSerializerNotFound(response: Response[Body]) = log.error("Can't serialize response: {}", response)

  protected def getRouteKey(requestMatcher: RequestMatcher, groupName: Option[String]) = {
    if (requestMatcher.uri.isEmpty)
      throw new IllegalArgumentException(s"uri is not set on matcher: $requestMatcher")
    val specificUri = requestMatcher.uri.get.pattern.specific // todo: implement other filters?

    groupName.map {
      specificUri + "#" + _
    } getOrElse specificUri
  }

  protected def safe(t: () => String): String = Try(t()).getOrElse("???")
}
