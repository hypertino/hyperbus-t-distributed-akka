package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.typesafe.config.{ConfigFactory, Config}
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration

object ActorSystemRegistry {
  private val registry = new TrieMap[String, (ActorSystem, AtomicInteger)]
  private val lock = new Object
  private val log = LoggerFactory.getLogger(this.getClass)
  import eu.inn.servicebus.util.ConfigUtils._

  def addRef(actorSystemName: String): ActorSystem = {
    registry.get(actorSystemName) map { as ⇒
      as._2.incrementAndGet()
      as._1
    } getOrElse {
      // synchronize expensive operation despite the fact that we use TrieMap
      lock.synchronized {
        val as = createActorSystem(actorSystemName)
        registry.put(actorSystemName, (as, new AtomicInteger(1)))
        as
      }
    }
  }

  def release(actorSystemName: String)(implicit timeout: FiniteDuration) = {
    registry.get(actorSystemName) foreach { a ⇒
      if (a._2.decrementAndGet() <= 0) {
        log.info(s"Shutting down ${a._1}")
        registry.remove(actorSystemName)
        a._1.shutdown()
        a._1.awaitTermination(timeout)
      }
    }
  }

  def get(actorSystemName: String): Option[ActorSystem] = registry.get(actorSystemName).map(_._1)

  private def createActorSystem(actorSystemName: String): ActorSystem = {
    val appConfig = ConfigFactory.load()
    val akkaConfig = if (appConfig.hasPath(s"actor-system-registry.$actorSystemName"))
      appConfig.getConfig(s"actor-system-registry.$actorSystemName")
    else
      appConfig

    val as = ActorSystem(actorSystemName, akkaConfig)
    as.registerOnTermination(registry.remove(actorSystemName))
    as
  }
}
