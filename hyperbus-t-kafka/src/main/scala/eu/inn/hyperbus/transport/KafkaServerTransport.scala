package eu.inn.hyperbus.transport

import java.io.ByteArrayInputStream
import java.util.Properties
import java.util.concurrent.{TimeUnit, ExecutorService, Executors}
import java.util.concurrent.atomic.AtomicLong

import com.typesafe.config.Config
import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.hyperbus.transport.kafkatransport.ConfigLoader
import eu.inn.hyperbus.util.ConfigUtils._
import kafka.consumer.{Consumer, ConsumerConfig, KafkaStream}
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.concurrent.duration._

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

  protected[this] val subscriptions = new TrieMap[Subscription, TopicSubscription[_]]
  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  def onCommand(matcher: TransportRequestMatcher,
                inputDeserializer: RequestDeserializer[Request[Body]])
               (handler: (Request[Body]) => Future[TransportResponse]): Future[Subscription] = ???

  def onEvent(matcher: TransportRequestMatcher,
              groupName: String,
              inputDeserializer: RequestDeserializer[Request[Body]])
             (handler: (Request[Body]) => Future[Unit]): Future[Subscription] = {

    routes.find(r ⇒ r.requestMatcher.matchRequestMatcher(matcher)) map { route ⇒

      val subscription = new TopicSubscription[Unit](1, /*todo: per topic thread count*/
        route, matcher, groupName, inputDeserializer, handler
      )
      subscriptions.put(subscription, subscription)
      subscription.run()
      Future.successful(subscription)
    } getOrElse {
      Future.failed(new NoTransportRouteException(s"Kafka consumer (server). matcher: $matcher"))
    }
  }

  override def off(subscription: Subscription): Future[Unit] = {
    subscriptions.remove(subscription) match {
      case Some(topicSubscription) ⇒ Future {
        topicSubscription.stop(0.seconds)
      }

      case None ⇒
        Future.failed(new IllegalArgumentException(s"Subscription not found: $subscription"))
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


  class TopicSubscription[OUT](
                           threadCount: Int,
                           route: KafkaRoute,
                           requestMatcher: TransportRequestMatcher,
                           groupName: String,
                           inputDeserializer: RequestDeserializer[Request[Body]],
                           handler: (Request[Body]) ⇒ Future[OUT]
                         ) extends Subscription {

    val consumer = {
      val props = consumerProperties.clone().asInstanceOf[Properties]
      val groupId = props.getProperty("group.id")
      val newGroupId = if (groupId != null) {
        groupId + "." + groupName
      }
      else {
        groupName
      }
      props.setProperty("group.id", newGroupId)
      Consumer.create(new ConsumerConfig(props))
    }
    @volatile var threadPool: ExecutorService = null

    def run(): Unit = {
      threadPool = Executors.newFixedThreadPool(threadCount)
      val consumerMap = consumer.createMessageStreams(Map(route.kafkaTopic → threadCount))
      val streams = consumerMap(route.kafkaTopic)

      streams.map { stream ⇒
        threadPool.submit(new Runnable {
          override def run(): Unit = consumeStream(stream)
        })
      }
    }

    def stop(duration: FiniteDuration): Unit = {
      consumer.commitOffsets
      consumer.shutdown()
      val t = threadPool
      if (t != null) {
        t.shutdown()
        if (duration.toMillis > 0) {
          try {
            t.awaitTermination(duration.toMillis, TimeUnit.MILLISECONDS)
          }
          catch {
            case t: InterruptedException ⇒ // .. do nothing
          }
        }
        threadPool = null
      }
    }

    def consumeMessage(consumerId: String, next: kafka.message.MessageAndMetadata[Array[Byte], Array[Byte]]): Unit = {
      val message = next.message()
      lazy val messageString = new String(message, encoding)
      try {
        val inputBytes = new ByteArrayInputStream(message)
        val input = MessageDeserializer.deserializeRequestWith(inputBytes)(inputDeserializer)
        if (requestMatcher.matchMessage(input)) { // todo: test order of matching?
          handler(input)
        } else {
          if (log.isTraceEnabled) {
            log.trace(s"Consumer #$consumerId. Skipped message from partiton#${next.partition}: $messageString")
          }
        }
      }
      catch {
        case NonFatal(e) ⇒
          log.error(s"Consumer #$consumerId can't deserialize message from partiton#${next.partition}: $messageString", e)
      }
    }

    private def consumeStream(stream: KafkaStream[Array[Byte], Array[Byte]]): Unit = {
      val consumerId = Thread.currentThread().getName
      log.info(s"Starting consumer #$consumerId on topic ${route.kafkaTopic} -> $requestMatcher}")
      try {
        val iterator = stream.iterator()
        while (iterator.hasNext()) {
          val next = iterator.next()
          consumeMessage(consumerId, next)
        }
        log.info(s"Stopping consumer #$consumerId on topic ${route.kafkaTopic}")
      }
      catch {
        case NonFatal(t) ⇒
          log.error(s"Consumer #$consumerId failed", t)
      }
    }
  }

}
