package eu.inn.hyperbus

import eu.inn.hyperbus.impl.MacroApi
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros

// todo: document API
trait HyperbusApi {
  def <~[REQ <: Request[Body]](request: REQ): Any = macro HyperbusMacro.ask[REQ]

  def <|[REQ <: Request[Body]](request: REQ): Future[PublishResult] = macro HyperbusMacro.publish[REQ]

  def |>[REQ <: Request[Body]](handler: (REQ) => Future[Unit]): Future[Subscription] = macro HyperbusMacro.onEvent[REQ]

  def ~>[REQ <: Request[Body]](handler: REQ => Future[Response[Body]]): Future[Subscription] = macro HyperbusMacro.onCommand[REQ]

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

  def onCommand[RESP <: Response[Body], REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                                              requestDeserializer: RequestDeserializer[REQ])
                                                             (handler: (REQ) => Future[RESP]): Future[Subscription]

  def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                    groupName: Option[String],
                                    requestDeserializer: RequestDeserializer[REQ])
                                   (handler: (REQ) => Future[Unit]): Future[Subscription]

  def onEventForGroup[REQ <: Request[Body]](groupName: String, handler: (REQ) => Future[Unit]): Future[Subscription] = macro HyperbusMacro.onEventForGroup[REQ]

  def off(subscription: Subscription): Future[Unit]

  def shutdown(duration: FiniteDuration): Future[Boolean]

  def macroApiImpl: MacroApi
}
