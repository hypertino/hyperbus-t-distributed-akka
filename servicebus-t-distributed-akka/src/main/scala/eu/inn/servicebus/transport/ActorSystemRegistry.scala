package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.typesafe.config.{ConfigFactory, Config}

import scala.collection.concurrent.TrieMap

object ActorSystemRegistry {
  private val registry = new TrieMap[String, (ActorSystem, AtomicInteger)]
  private val lock = new Object
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

  def release(actorSystemName: String) = {
    registry.get(actorSystemName) map { a ⇒
      if (a._2.decrementAndGet() <= 0) {
        a._1.shutdown()
        registry.remove(actorSystemName)
      }
    }
  }

  def get(actorSystemName: String): Option[ActorSystem] = registry.get(actorSystemName).map(_._1)

  private def createActorSystem(actorSystemName: String): ActorSystem = {
    val appConfig = ConfigFactory.load()
    val akkaConfig = if (appConfig.hasNot(s"actor-system-registry.$actorSystemName"))
      appConfig
    else
      appConfig.getConfig(s"actor-system-registry.$actorSystemName")
    val as = ActorSystem(actorSystemName, akkaConfig)
    as.registerOnTermination(registry.remove(actorSystemName))
    as
  }
}
