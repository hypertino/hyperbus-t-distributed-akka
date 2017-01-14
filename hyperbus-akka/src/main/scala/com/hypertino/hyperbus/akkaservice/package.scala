package com.hypertino.hyperbus

import com.hypertino.hyperbus.model.{Body, Request}
import com.hypertino.hyperbus.transport.api.Subscription
import rx.lang.scala.Observer

import scala.concurrent.Future

package object akkaservice {

  import akka.actor.ActorRef

  import language.experimental.macros

  implicit class ImplicitRouter(val hyperbus: Hyperbus) {
    // todo: + implicit route options (groupName mapping to runtime group)
    def routeTo[A](actorRef: ActorRef): Future[List[Subscription]] = macro AkkaHyperServiceMacro.routeTo[A]
  }

}
