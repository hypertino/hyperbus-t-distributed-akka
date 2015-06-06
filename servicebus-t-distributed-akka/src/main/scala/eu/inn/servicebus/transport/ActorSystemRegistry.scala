package eu.inn.servicebus.transport

import akka.actor.ActorSystem
import com.typesafe.config.{ConfigFactory, Config}

import scala.collection.concurrent.TrieMap

object ActorSystemRegistry {
  private val registry = new TrieMap[String, ActorSystem]
  private val lock = new Object
  import eu.inn.servicebus.util.ConfigUtils._

  def getOrCreate(actorSystemName: String): ActorSystem = {
    get(actorSystemName) getOrElse {
      // specifically synchronize expensive operation despite the fact that we use TrieMap
      lock.synchronized {
        val as = createActorSystem(actorSystemName)
        registry.put(actorSystemName, as)
        as
      }
    }
  }

  def get(actorSystemName: String): Option[ActorSystem] = registry.get(actorSystemName)

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
