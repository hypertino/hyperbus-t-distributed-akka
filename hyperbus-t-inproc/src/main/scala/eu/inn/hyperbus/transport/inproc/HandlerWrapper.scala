package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import rx.lang.scala.Observer

import scala.concurrent.Future

private[transport] case class HandlerWrapper(inputDeserializer: RequestDeserializer[Request[Body]],
                                             handler: Either[Request[Body] => Future[TransportResponse], Observer[Request[Body]]]) {
  def requestHandler = handler.left.get
  def eventHandler = handler.right.get
}
