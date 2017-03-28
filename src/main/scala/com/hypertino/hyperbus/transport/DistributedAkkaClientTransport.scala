package com.hypertino.hyperbus.transport

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.util.Timeout
import com.hypertino.hyperbus.model.{RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization.{MessageReader, ResponseBaseDeserializer}
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.distributedakka._
import com.hypertino.hyperbus.util.ConfigUtils._
import com.typesafe.config.Config
import monix.eval.Task
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DistributedAkkaClientTransport(val actorSystem: ActorSystem,
                                     val localAffinity: Boolean = true,
                                     val actorSystemRegistryKey: Option[String] = None,
                                     implicit val timeout: Timeout = Util.defaultTimeout) extends ClientTransport {

  private def this(actorSystemWrapper: ActorSystemWrapper, localAffinity: Boolean, timeout: Timeout) =
    this(actorSystemWrapper.actorSystem, localAffinity, Some(actorSystemWrapper.key), timeout)

  def this(config: Config) = this(ActorSystemRegistry.addRef(config),
    localAffinity = config.getOptionBoolean("local-afinity") getOrElse true,
    new Timeout(config.getOptionDuration("timeout") getOrElse Util.defaultTimeout)
  )

  protected[this] val cluster = Cluster(actorSystem)
  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  import actorSystem._

  val noRouteActor = actorSystem.actorSelection("no-route-watcher").resolveOne().recover {
    case _ ⇒ actorSystem.actorOf(Props(new NoRouteWatcher), "no-route-watcher")
  }

  protected[this] val mediator = DistributedPubSub(actorSystem).mediator


  def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Task[ResponseBase] = {
    val content = message.serializeToString
    val request = HyperbusRequest(content)

    import actorSystem.dispatcher
    Task.fromFuture(
      akka.pattern.ask(mediator, Publish(message.headers.hri.serviceAddress, request, sendOneMessageToEachGroup = true)) map {
        case result: HyperbusResponse ⇒
          MessageReader.from(result.content, responseDeserializer)
      }
    )
  }

  def publish(message: RequestBase): Task[PublishResult] = {
    val content = message.serializeToString
    val request = HyperbusRequest(content)

    mediator ! Publish(message.headers.hri.serviceAddress, request, sendOneMessageToEachGroup = true) // todo: At least one confirm?
    Task.now {
      new PublishResult {
        def sent = None
        def offset = None
        override def toString = s"PublishResult(sent=None,offset=None)"
      }
    }
  }

  override def shutdown(duration: FiniteDuration): Task[Boolean] = {
    log.info("Shutting down DistributedAkkaClientTransport...")
    actorSystemRegistryKey foreach { key ⇒
      log.debug(s"DistributedAkkaClientTransport: releasing ActorSystem(${actorSystem.name}) key: $key")
      ActorSystemRegistry.release(key)(duration)
    }
    Task.now(true)
  }
}

