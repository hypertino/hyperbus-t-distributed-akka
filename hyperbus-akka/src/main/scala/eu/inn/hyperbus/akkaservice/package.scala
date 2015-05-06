package eu.inn.hyperbus

package object akkaservice {

  import akka.actor.ActorRef

  import language.experimental.macros

  implicit class ImplicitRouter(val hyperBus: HyperBus) {
    def routeTo[A](actorRef: ActorRef): List[String] = macro AkkaHyperServiceMacro.routeTo[A]

    def routeTo[A](actorRef: ActorRef, groupName: String): List[String] = macro AkkaHyperServiceMacro.routeWithGroupTo[A]
  }
}
