package eu.inn.hyperbus

import eu.inn.hyperbus.transport.api.Subscription

import scala.concurrent.Future

package object akkaservice {

  import akka.actor.ActorRef

  import language.experimental.macros

  implicit class ImplicitRouter(val hyperBus: HyperBus) {
    // todo: + implicit route options (groupName mapping to runtime group)
    def routeTo[A](actorRef: ActorRef): Future[List[Subscription]] = macro AkkaHyperServiceMacro.routeTo[A]
  }

}
