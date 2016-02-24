package eu.inn.hyperbus

import eu.inn.hyperbus.impl.MacroApi
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros

// todo: document API
trait HyperBusApi {
  def <~[REQ <: Request[Body]](request: REQ) = macro HyperBusMacro.ask[REQ]
  def <|[REQ <: Request[Body]](request: REQ): Future[PublishResult] = macro HyperBusMacro.publish[REQ]
  def |>[REQ <: Request[Body]](handler: (REQ) => Future[Unit]): String = macro HyperBusMacro.onEvent[REQ]
  def ~>[REQ <: Request[Body]](handler: REQ => Future[Response[Body]]): String = macro HyperBusMacro.onCommand[REQ]

  def <~(request: DynamicRequest): Future[Response[DynamicBody]] = {
    ask(request,
      macroApiImpl.responseDeserializer(_, _, PartialFunction.empty)
    ).asInstanceOf[Future[Response[DynamicBody]]]
  }

  def <|(request: DynamicRequest): Future[PublishResult] = {
    publish(request)
  }

  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        responseDeserializer: ResponseDeserializer[RESP]): Future[RESP]

  def publish[REQ <: Request[Body]](request: REQ): Future[PublishResult]

  def onCommand[RESP <: Response[Body], REQ <: Request[Body]](requestMatcher: TransportRequestMatcher,
                                                              requestDeserializer: RequestDeserializer[REQ])
                                                             (handler: (REQ) => Future[RESP]): String

  def onEvent[REQ <: Request[Body]](requestMatcher: TransportRequestMatcher,
                                    groupName: Option[String],
                                    requestDeserializer: RequestDeserializer[REQ])
                                   (handler: (REQ) => Future[Unit]): String

  def onEventForGroup[REQ <: Request[Body]](groupName: String, handler: (REQ) => Future[Unit]): String = macro HyperBusMacro.onEventForGroup[REQ]

  def off(subscriptionId: String): Unit

  def shutdown(duration: FiniteDuration): Future[Boolean]

  def macroApiImpl: MacroApi
}
