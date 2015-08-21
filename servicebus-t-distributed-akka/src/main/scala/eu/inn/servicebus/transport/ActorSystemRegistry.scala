package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Props, ActorLogging, Actor, ActorSystem}
import akka.cluster.ClusterEvent._
import akka.cluster.{UniqueAddress, Cluster}
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import com.typesafe.config.{ConfigFactory, Config}
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.FiniteDuration

object ActorSystemRegistry {
  private val registry = new TrieMap[String, (ActorSystem, AtomicInteger)]
  private val lock = new Object
  private val log = LoggerFactory.getLogger(this.getClass)
  import eu.inn.servicebus.util.ConfigUtils._

  def addRef(config: Config): ActorSystem = {
    val actorSystemName = config.getString("actor-system", "eu-inn")
    registry.get(actorSystemName) map { as ⇒
      as._2.incrementAndGet()
      as._1
    } getOrElse {
      // synchronize expensive operation despite the fact that we use TrieMap
      lock.synchronized {
        val as = createActorSystem(actorSystemName, config)
        registry.put(actorSystemName, (as, new AtomicInteger(1)))
        as
      }
    }
  }

  def release(actorSystemName: String)(implicit timeout: FiniteDuration) = {
    registry.get(actorSystemName) foreach { a ⇒
      if (a._2.decrementAndGet() <= 0) {
        registry.remove(actorSystemName)
        shutdownClusterNode(a._1)
      }
    }
  }

  def get(actorSystemName: String): Option[ActorSystem] = registry.get(actorSystemName).map(_._1)

  private def createActorSystem(actorSystemName: String, akkaConfig: Config): ActorSystem = {
    val as = ActorSystem(actorSystemName, akkaConfig)
    as.registerOnTermination(registry.remove(actorSystemName))
    as
  }

  def shutdownClusterNode(actorSystem: ActorSystem)(implicit timeout: FiniteDuration): Unit = {
    val exitPromise = Promise[Boolean]()
    val cluster = Cluster(actorSystem)
    val me = cluster.selfUniqueAddress
    val exitEventListener = actorSystem.actorOf(Props(new ExitEventListener(exitPromise, me)))
    log.info(s"Leaving cluster $me...")
    cluster.leave(me.address)

    val result = Await.result(exitPromise.future, timeout/2)
    if (!result)
      log.warn(s"Didn't get confirmation that node left: $me")

    log.info(s"Shutting down $actorSystem...")
    actorSystem.shutdown()
    actorSystem.awaitTermination(timeout)
  }

  class ExitEventListener(val exited: Promise[Boolean], nodeAddress: UniqueAddress) extends Actor with ActorLogging {
    val cluster = Cluster(context.system)

    override def preStart(): Unit = {
      cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[ClusterDomainEvent])
    }

    override def postStop(): Unit = {
      cluster.unsubscribe(self)
      if (!exited.isCompleted)
        exited.success(false)
    }

    def receive = {
      case e: MemberExited ⇒
        if (e.member.uniqueAddress == nodeAddress) {
          exited.success(true)
          log.info(s"Node exited: ${e.member.uniqueAddress}")
        }
      case _ ⇒ //ignore
    }
  }
}
