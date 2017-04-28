package com.hypertino.hyperbus.transport

import akka.actor._
import akka.cluster.Cluster
import akka.pattern.gracefulStop
import akka.util.Timeout
import com.hypertino.hyperbus.model.RequestBase
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.transport.distributedakka._
import com.hypertino.hyperbus.util.ConfigUtils._
import com.typesafe.config.Config
import monix.eval.Task
import monix.reactive.Observable
import org.slf4j.LoggerFactory
import scaldi.Injector

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

class DistributedAkkaServerTransport(val actorSystem: ActorSystem,
                                     implicit val scheduler: monix.execution.Scheduler,
                                     implicit val timeout: Timeout = Util.defaultTimeout)
  extends ServerTransport {

  def this(config: Config, injector: Injector) = this(
    actorSystem = ActorSystemInjector(config.getOptionString("actor-system"))(injector),
    scheduler = monix.execution.Scheduler.Implicits.global, // todo: configurable
    timeout = new Timeout(config.getOptionDuration("timeout") getOrElse Util.defaultTimeout)
  )

  protected[this] val cluster = Cluster(actorSystem)
  protected[this] val log = LoggerFactory.getLogger(this.getClass)
  protected[this] val subscriptionManager = actorSystem.actorOf(Props(classOf[distributedakka.SubscriptionManager]))

  override def commands[REQ <: RequestBase](matcher: RequestMatcher,
                                            inputDeserializer: RequestDeserializer[REQ]): Observable[CommandEvent[REQ]] = {
    new CommandHyperbusSubscription(matcher, inputDeserializer, subscriptionManager)
      .observable
      .asInstanceOf[Observable[CommandEvent[REQ]]]
  }

  override def events[REQ <: RequestBase](matcher: RequestMatcher,
                                          groupName: String,
                                          inputDeserializer: RequestDeserializer[REQ]): Observable[REQ] = {

    new EventHyperbusSubscription(matcher, groupName, inputDeserializer, subscriptionManager)
      .observable
      .asInstanceOf[Observable[REQ]]
  }

  def shutdown(duration: FiniteDuration): Task[Boolean] = {
    log.info("Shutting down DistributedAkkaServerTransport...")
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

    Task.fromFuture(futureStopManager).timeout(duration)
  }
}

