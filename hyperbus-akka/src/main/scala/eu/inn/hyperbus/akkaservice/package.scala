package eu.inn.hyperbus

package object akkaservice {

  import akka.actor.ActorRef

  import language.experimental.macros

  implicit class ImplicitRouter(val hyperBus: HyperBus) {
    // todo: + implicit route options (groupName mapping to runtime group)
    def routeTo[A](actorRef: ActorRef): List[String] = macro AkkaHyperServiceMacro.routeTo[A]
  }

}
