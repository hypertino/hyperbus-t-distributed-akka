package com.hypertino.hyperbus.transport.distributedakka

import akka.actor.ActorSystem
import scaldi.{Injectable, Injector}

class ActorSystemInjector(implicit val inj: Injector) extends Injectable{
  def actorSystem(name: Option[String]): ActorSystem = name match {
    case None ⇒ inject[ActorSystem]
    case Some(s) ⇒ inject[ActorSystem] (identified by s and by default inject[ActorSystem])
  }
}

object ActorSystemInjector {
  def apply(name: Option[String] = None)(implicit inj: Injector) : ActorSystem = {
    new ActorSystemInjector()
      .actorSystem(name)
  }
}
