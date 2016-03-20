package eu.inn.hyperbus.transport.distributedakka

import akka.actor.{Actor, DeadLetter}
import eu.inn.hyperbus.transport.api.NoTransportRouteException
import akka.pattern.pipe

import scala.concurrent.Future

private[transport] class NoRouteWatcher extends Actor {
  import context._
  system.eventStream.subscribe(self, classOf[DeadLetter])

  override def receive: Receive = {
    case DeadLetter(message: HyperbusRequest, messageSender, recipient) ⇒
      Future.failed(new NoTransportRouteException(recipient.toString())) pipeTo messageSender
  }
}