package com.hypertino.hyperbus.transport.distributedakka

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.pubsub.{DistributedPubSubMediator, DistributedPubSubSettings}
import akka.dispatch.Dispatchers

/*
* TODO: remove this class if akka accepts and implements this:
* https://github.com/akka/akka/issues/19009
* */
class DistributedPubSubMediatorEx(settings: DistributedPubSubSettings) extends DistributedPubSubMediator(settings) {
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
  def props(settings: DistributedPubSubSettings): Props =
    Props(new DistributedPubSubMediatorEx(settings)).withDeploy(Deploy.local)
}

class DistributedPubSubEx(system: ExtendedActorSystem) extends Extension {
  private val settings = DistributedPubSubSettings(system)

  def isTerminated: Boolean =
    Cluster(system).isTerminated || !settings.role.forall(Cluster(system).selfRoles.contains)

  val mediator: ActorRef = {
    if (isTerminated)
      system.deadLetters
    else {
      val name = system.settings.config.getString("akka.cluster.pub-sub.name")
      val dispatcher = system.settings.config.getString("akka.cluster.pub-sub.use-dispatcher") match {
        case "" ⇒ Dispatchers.DefaultDispatcherId
        case id ⇒ id
      }
      system.systemActorOf(DistributedPubSubMediatorEx.props(settings).withDispatcher(dispatcher), name)
    }
  }
}

object DistributedPubSubEx extends ExtensionId[DistributedPubSubEx] with ExtensionIdProvider {
  override def get(system: ActorSystem): DistributedPubSubEx = super.get(system)

  override def lookup = DistributedPubSubEx

  override def createExtension(system: ExtendedActorSystem): DistributedPubSubEx =
    new DistributedPubSubEx(system)
}