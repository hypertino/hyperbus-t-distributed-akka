package eu.inn.hyperbus.transport.distributedakka

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.DistributedPubSubMediator
import akka.routing._

import scala.concurrent.duration._

/*
* TODO: remove this class if akka accepts and implements this:
* https://github.com/akka/akka/issues/19009
* */
class DistributedPubSubMediatorEx(
                                   role: Option[String],
                                   routingLogic: RoutingLogic,
                                   gossipInterval: FiniteDuration,
                                   removedTimeToLive: FiniteDuration,
                                   maxDeltaElements: Int) extends DistributedPubSubMediator(role, routingLogic, gossipInterval, removedTimeToLive, maxDeltaElements) {
  override def publishToEachGroup(path: String, msg: Any): Unit = {
    val prefix = path + '/'
    val lastKey = path + '0' // '0' is the next char of '/'
    val hasAtLeastOneReceiver = registry.exists(_._2.content.range(prefix, lastKey).nonEmpty)
    if (hasAtLeastOneReceiver) {
      super.publishToEachGroup(path, msg)
    } else {
      context.system.deadLetters ! DeadLetter(msg, sender(), context.self)
    }
  }
}

object DistributedPubSubMediatorEx {
  def props(
             role: Option[String],
             routingLogic: RoutingLogic = RandomRoutingLogic(),
             gossipInterval: FiniteDuration = 1.second,
             removedTimeToLive: FiniteDuration = 2.minutes,
             maxDeltaElements: Int = 3000): Props =
    Props(classOf[DistributedPubSubMediatorEx], role, routingLogic, gossipInterval, removedTimeToLive, maxDeltaElements)
}

class DistributedPubSubExtensionEx(system: ExtendedActorSystem) extends Extension {
  private val config = system.settings.config.getConfig("akka.contrib.cluster.pub-sub")
  private val role: Option[String] = config.getString("role") match {
    case "" ⇒ None
    case r  ⇒ Some(r)
  }

  def isTerminated: Boolean = Cluster(system).isTerminated || !role.forall(Cluster(system).selfRoles.contains)

  val mediator: ActorRef = {
    if (isTerminated)
      system.deadLetters
    else {
      val routingLogic = config.getString("routing-logic") match {
        case "random"             ⇒ RandomRoutingLogic()
        case "round-robin"        ⇒ RoundRobinRoutingLogic()
        case "consistent-hashing" ⇒ throw new IllegalArgumentException(s"'consistent-hashing' routing logic can't be used by the pub-sub mediator")
        case "broadcast"          ⇒ BroadcastRoutingLogic()
        case other                ⇒ throw new IllegalArgumentException(s"Unknown 'routing-logic': [$other]")
      }
      val gossipInterval = config.getDuration("gossip-interval", MILLISECONDS).millis
      val removedTimeToLive = config.getDuration("removed-time-to-live", MILLISECONDS).millis
      val maxDeltaElements = config.getInt("max-delta-elements")
      val name = config.getString("name")
      system.actorOf(DistributedPubSubMediatorEx.props(role, routingLogic, gossipInterval, removedTimeToLive, maxDeltaElements),
        name)
    }
  }
}

object DistributedPubSubExtensionEx extends ExtensionId[DistributedPubSubExtensionEx] with ExtensionIdProvider {
  override def get(system: ActorSystem): DistributedPubSubExtensionEx = super.get(system)

  override def lookup = DistributedPubSubExtensionEx

  override def createExtension(system: ExtendedActorSystem): DistributedPubSubExtensionEx =
    new DistributedPubSubExtensionEx(system)
}