package eu.inn.forgame.statusmonitor

import akka.actor.{ActorLogging, Actor, Props}
import akka.contrib.pattern.{DistributedPubSubMediator, DistributedPubSubExtension}
import akka.util.Timeout
import eu.inn.datamodel.forgame.{user => User}
import eu.inn.util.ConfigComponent
import eu.inn.util.akka.ActorSystemComponent
import eu.inn.util.format.JsonSupport
import eu.inn.util.servicebus.{ServiceBusComponent, ServiceConsumerComponent}

import scala.concurrent.duration._

trait StatusMonitorComponent {
  this: ActorSystemComponent
    with ConfigComponent
    with ServiceConsumerComponent
    with ServiceBusComponent ⇒

  lazy val statusMonitorService =
    actorSystem.actorOf(Props(new StatusMonitorService), name = "status-monitor")

  class StatusMonitorService extends Actor with ActorLogging {
    import DistributedPubSubMediator.{ Subscribe, SubscribeAck }
    val topic = "contentX"
    val mediator = DistributedPubSubExtension(context.system).mediator

    // subscribe to the topic
    mediator ! Subscribe(topic, self)

    def receive = {
      case SubscribeAck(Subscribe(topic, None, `self`)) ⇒
        log.info("Subscribing!")
        context become ready
      case _ =>
        log.info("GotY")
    }

    def ready: Actor.Receive = {
      case s: String ⇒
        log.info(s"Got $s replying to ${sender()}")
        sender() ! "reply:" + s
      case _ =>
        log.info("GotX")
    }
  }
}
