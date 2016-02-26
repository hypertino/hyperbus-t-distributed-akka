package eu.inn.hyperbus.transport

import akka.actor._
import akka.cluster.Cluster
import akka.pattern.{ask, gracefulStop}
import akka.util.Timeout
import com.typesafe.config.Config
import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.transport.distributedakka._
import eu.inn.hyperbus.util.ConfigUtils._
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

class DistributedAkkaServerTransport(val actorSystem: ActorSystem,
                                     val actorSystemRegistryKey: Option[String] = None,
                                     implicit val timeout: Timeout = Util.defaultTimeout)
  extends ServerTransport {

  private def this(actorSystemWrapper: ActorSystemWrapper, timeout: Timeout) =
    this(actorSystemWrapper.actorSystem, Some(actorSystemWrapper.key))

  def this(config: Config) = this(
    actorSystemWrapper = ActorSystemRegistry.addRef(config),
    timeout = new Timeout(config.getOptionDuration("timeout") getOrElse Util.defaultTimeout)
  )

  protected[this] val cluster = Cluster(actorSystem)
  protected[this] val log = LoggerFactory.getLogger(this.getClass)
  protected[this] val subscriptionManager = actorSystem.actorOf(Props(classOf[distributedakka.SubscriptionManager]), "d-akka-subscription-mgr")

  override def onCommand(requestMatcher: RequestMatcher,
                         inputDeserializer: RequestDeserializer[Request[Body]])
                        (handler: (Request[Body]) => Future[TransportResponse]): Future[Subscription] = {

    (subscriptionManager ? CommandSubscription(requestMatcher, inputDeserializer, handler)).asInstanceOf[Future[Subscription]]
  }

  override def onEvent(requestMatcher: RequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[Request[Body]])
                      (handler: (Request[Body]) => Future[Unit]): Future[Subscription] = {
    (subscriptionManager ? EventSubscription(requestMatcher, groupName, inputDeserializer, handler)).asInstanceOf[Future[Subscription]]
  }

  override def off(subscription: Subscription): Future[Unit] = {
    (subscriptionManager ? UnsubscribeCommand(subscription)).asInstanceOf[Future[Unit]]
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    log.info("Shutting down DistributedAkkaServerTransport...")
    import actorSystem.dispatcher
    val futureStopManager = try {
      gracefulStop(subscriptionManager, duration) recover {
        case t: Throwable ⇒
          log.error("Shutting down distributed akka", t)
          false
      }
    } catch {
      case NonFatal(e) ⇒
        log.error(s"Can't gracefully stop subscriptionManager", e)
        Future.successful(false)
    }

    futureStopManager map { result ⇒
      actorSystemRegistryKey foreach { key ⇒
        log.debug(s"DistributedAkkaServerTransport: releasing ActorSystem(${actorSystem.name}) key: $key")
        ActorSystemRegistry.release(key)(duration)
      }
      result
    }
  }
}

