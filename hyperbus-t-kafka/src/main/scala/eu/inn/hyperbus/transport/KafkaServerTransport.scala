package eu.inn.hyperbus.transport

import java.util.Properties

import com.typesafe.config.Config
import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.transport.kafkatransport._
import eu.inn.hyperbus.util.ConfigUtils._
import org.slf4j.LoggerFactory
import rx.lang.scala.Observer

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class KafkaServerTransport(
                            consumerProperties: Properties,
                            routes: List[KafkaRoute],
                            encoding: String = "UTF-8")
                          (implicit val executionContext: ExecutionContext) extends ServerTransport {
  def this(config: Config) = this(
    consumerProperties = ConfigLoader.loadConsumerProperties(config.getConfig("consumer")),
    routes = ConfigLoader.loadRoutes(config.getConfigList("routes")),
    encoding = config.getOptionString("encoding") getOrElse "UTF-8"
  )(scala.concurrent.ExecutionContext.global) // todo: configurable ExecutionContext like in akka?

  protected[this] val subscriptions = mutable.Map[TopicSubscriptionKey, TopicSubscription[Request[Body]]]()
  protected[this] val lock = new Object
  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  override def onCommand(matcher: RequestMatcher,
                         inputDeserializer: RequestDeserializer[Request[Body]])
                        (handler: (Request[Body]) => Future[TransportResponse]): Future[Subscription] = ???

  override def onEvent[REQ <: Request[Body]](matcher: RequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[REQ],
                       subscriber: Observer[REQ]): Future[Subscription] = {

    routes.find(r ⇒ r.requestMatcher.matchRequestMatcher(matcher)) map { route ⇒
      val key = TopicSubscriptionKey(route.kafkaTopic, route.kafkaPartitionKeys, groupName)
      val underlyingSubscription = UnderlyingSubscription(matcher, inputDeserializer, subscriber)
      lock.synchronized {
        subscriptions.get(key) match {
          case Some(subscription) ⇒
            val nextId = subscription.addUnderlying(underlyingSubscription.asInstanceOf[UnderlyingSubscription[Request[Body]]])
            Future.successful(KafkaTransportSubscription(key, nextId))

          case None ⇒
            val subscription = new TopicSubscription[Request[Body]](consumerProperties, encoding, 1, /*todo: per topic thread count*/
              route, groupName)
            subscriptions.put(key, subscription)
            val nextId = subscription.addUnderlying(underlyingSubscription.asInstanceOf[UnderlyingSubscription[Request[Body]]])
            subscription.run()
            Future.successful(KafkaTransportSubscription(key, nextId))
        }
      }
    } getOrElse {
      Future.failed(new NoTransportRouteException(s"Kafka consumer (server). matcher: $matcher"))
    }
  }

  override def off(subscription: Subscription): Future[Unit] = {
    subscription match {
      case kafkaS: KafkaTransportSubscription ⇒
        val notFoundException = new IllegalArgumentException(s"Subscription not found: $subscription")
        lock.synchronized {
          subscriptions.get(kafkaS.key) match {
            case Some(s) ⇒
              if (s.removeUnderlying(kafkaS.underlyingId)) { // last subscription, terminate kafka subscription
                subscriptions.remove(kafkaS.key)
                Future {
                  s.stop(1.seconds)
                }
              }
              else {
                Future.successful{}
              }
            case None ⇒
              Future.failed(notFoundException)
          }
        }
      case _ ⇒
        Future.failed(new IllegalArgumentException(s"Expected KafkaTransportSubscription instead of: $subscription"))
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    val futures = subscriptions.map { kv ⇒
      Future {
        kv._2.stop(duration)
      }
    }
    subscriptions.clear()
    Future.sequence(futures) map { _ ⇒
      true
    }
  }
}








