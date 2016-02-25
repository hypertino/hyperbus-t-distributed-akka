package eu.inn.hyperbus.transport.distributedakka

import akka.actor.{DeadLetter, Actor}
import akka.pattern.pipe
import eu.inn.hyperbus.transport.api.NoTransportRouteException

import scala.concurrent.Future
