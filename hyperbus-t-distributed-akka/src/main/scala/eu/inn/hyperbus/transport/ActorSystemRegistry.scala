package eu.inn.hyperbus.transport

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, MemberStatus, UniqueAddress}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Promise}

case class ActorSystemWrapper(key: String, actorSystem: ActorSystem)

object ActorSystemRegistry {
  private val registry = new TrieMap[String, (ActorSystem, AtomicInteger, Promise[Boolean])]
  private val lock = new Object
  private val log = LoggerFactory.getLogger(this.getClass)

  import eu.inn.hyperbus.util.ConfigUtils._

  def addRef(config: Config): ActorSystemWrapper = {
    val actorSystemName = config.getString("actor-system-name", "eu-inn")
    val actorSystemKey = config.getString("actor-system-key", "eu-inn")
    registry.get(actorSystemKey) map { as ⇒
      as._2.incrementAndGet()
      ActorSystemWrapper(actorSystemKey, as._1)
    } getOrElse {
      // synchronize expensive operation despite the fact that we use TrieMap
      lock.synchronized {
        val as = createActorSystem(actorSystemName, actorSystemKey, config)
        val exitPromise = Promise[Boolean]() // todo: maybe we don't need this in akka 2.4
        as.actorOf(Props(new ExitEventListener(exitPromise, Cluster(as).selfUniqueAddress)))
        registry.put(actorSystemKey, (as, new AtomicInteger(1), exitPromise))
        ActorSystemWrapper(actorSystemKey, as)
      }
    }
  }

  def release(actorSystemKey: String)(implicit timeout: FiniteDuration) = {
    registry.get(actorSystemKey) foreach { a ⇒
      if (a._2.decrementAndGet() <= 0) {
        registry.remove(actorSystemKey)
        shutdownClusterNode(a._1, a._3)
      }
    }
  }

  def get(actorSystemKey: String): Option[ActorSystem] = registry.get(actorSystemKey).map(_._1)

  private def createActorSystem(actorSystemName: String, actorSystemKey: String, akkaConfig: Config): ActorSystem = {
    val as = ActorSystem(actorSystemName, akkaConfig)
    as.registerOnTermination(registry.remove(actorSystemKey))
    as
  }

  def shutdownClusterNode(actorSystem: ActorSystem, exitPromise: Promise[Boolean])(implicit timeout: FiniteDuration): Unit = {
    val cluster = Cluster(actorSystem)
    val me = cluster.selfUniqueAddress

    import JavaConversions._
    val clusterMembers = cluster.state.getMembers.toSeq
    log.info(s"$me leaving cluster [" + clusterMembers.mkString(",") + "]")
    cluster.leave(me.address)

    if (clusterMembers.exists(_.status == MemberStatus.up)) {
      val result = try {
        Await.result(exitPromise.future, timeout / 2)
      } catch {
        case x: Throwable ⇒
          log.error(s"Timeout while waiting cluster leave for $me", x)
          false
      }
      if (!result)
        log.warn(s"Didn't get confirmation that node left: $me")
    } else {
      log.warn(s"No member with status 'up' - don't wait for the confirmation")
    }

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
