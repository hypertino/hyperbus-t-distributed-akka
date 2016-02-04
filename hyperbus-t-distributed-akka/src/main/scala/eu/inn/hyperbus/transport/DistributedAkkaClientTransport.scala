package eu.inn.hyperbus.transport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.{Props, ActorSystem}
import akka.cluster.Cluster
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.util.Timeout
import com.typesafe.config.Config
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.distributedakka.{NoRouteWatcher, DistributedPubSubExtensionEx, Util}
import eu.inn.hyperbus.util.ConfigUtils._
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DistributedAkkaClientTransport(val actorSystem: ActorSystem,
                                     val localAffinity: Boolean = true,
                                     val logMessages: Boolean = false,
                                     val actorSystemRegistryKey: Option[String] = None,
                                     implicit val timeout: Timeout = Util.defaultTimeout) extends ClientTransport {

  private def this(actorSystemWrapper: ActorSystemWrapper, localAffinity: Boolean,
                   logMessages: Boolean, timeout: Timeout) =
    this(actorSystemWrapper.actorSystem, localAffinity, logMessages, Some(actorSystemWrapper.key), timeout)

  def this(config: Config) = this(ActorSystemRegistry.addRef(config),
    localAffinity = config.getOptionBoolean("local-afinity") getOrElse true,
    logMessages = config.getOptionBoolean("log-messages") getOrElse false,
    new Timeout(config.getOptionDuration("timeout") getOrElse Util.defaultTimeout)
  )

  protected[this] val cluster = Cluster(actorSystem)
  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  import actorSystem._
  val noRouteActor = actorSystem.actorSelection("no-route-watcher").resolveOne().recover {
    case _ ⇒ actorSystem.actorOf(Props(new NoRouteWatcher), "no-route-watcher")
  }

  protected[this] val mediator = DistributedPubSubExtensionEx(actorSystem).mediator


  override def ask[OUT <: TransportResponse](message: TransportRequest, outputDeserializer: Deserializer[OUT]): Future[OUT] = {

    val specificUri = message.uri.pattern.specific
    val inputBytes = new ByteArrayOutputStream()
    message.serialize(inputBytes)
    val messageString = inputBytes.toString(Util.defaultEncoding)
    import eu.inn.hyperbus.util.LogUtils._

    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("requestId" → messageString.hashCode.toHexString), s"hyperBus <~ $messageString")
    }

    import actorSystem.dispatcher
    akka.pattern.ask(mediator, Publish(specificUri, messageString, sendOneMessageToEachGroup = true)) map {
      case result: String ⇒
        if (logMessages && log.isTraceEnabled) {
          log.trace(Map("requestId" → messageString.hashCode.toHexString), s"hyperBus ~(R)~> $result")
        }
        val outputBytes = new ByteArrayInputStream(result.getBytes(Util.defaultEncoding))
        outputDeserializer(outputBytes)
      // todo: case _ ⇒
    }
  }

  override def publish(message: TransportRequest): Future[PublishResult] = {
    val specificUri = message.uri.pattern.specific
    val inputBytes = new ByteArrayOutputStream()
    message.serialize(inputBytes)
    val messageString = inputBytes.toString(Util.defaultEncoding)

    if (logMessages && log.isTraceEnabled) {
      log.trace(s"hyperBus <| $messageString")
    }

    mediator ! Publish(specificUri, messageString, sendOneMessageToEachGroup = true) // todo: At least one confirm?
    Future.successful {
      new PublishResult {
        def sent = None

        def offset = None
      }
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    log.info("Shutting down DistributedAkkaClientTransport...")
    actorSystemRegistryKey foreach { key ⇒
      log.debug(s"DistributedAkkaClientTransport: releasing ActorSystem(${actorSystem.name}) key: $key")
      ActorSystemRegistry.release(key)(duration)
    }
    Future.successful(true)
  }
}

