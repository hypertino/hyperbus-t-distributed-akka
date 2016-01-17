package eu.inn.hyperbus.transport

import java.io.ByteArrayInputStream
import java.util.Properties
import java.util.concurrent.{TimeUnit, ExecutorService, Executors}
import java.util.concurrent.atomic.AtomicLong

import com.typesafe.config.Config
import eu.inn.hyperbus.transport.api._
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
                            logMessages: Boolean = false,
                            encoding: String = "UTF-8")
                          (implicit val executionContext: ExecutionContext) extends ServerTransport {
  def this(config: Config) = this(
    consumerProperties = ConfigLoader.loadConsumerProperties(config.getConfig("consumer")),
    routes = ConfigLoader.loadRoutes(config.getConfigList("routes")),
    logMessages = config.getOptionBoolean("log-messages") getOrElse false,
    encoding = config.getOptionString("encoding") getOrElse "UTF-8"
  )(scala.concurrent.ExecutionContext.global) // todo: configurable ExecutionContext like in akka?

  protected[this] val subscriptions = new TrieMap[String, Subscription[_, _]]
  protected[this] val idCounter = new AtomicLong(0)
  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  override def process[IN <: TransportRequest](uriFilter: Uri, inputDeserializer: Deserializer[IN], exceptionSerializer: Serializer[Throwable])
                                              (handler: (IN) => Future[TransportResponse]): String = ???

  override def subscribe[IN <: TransportRequest](uriFilter: Uri, groupName: String, inputDeserializer: Deserializer[IN])
                                                (handler: (IN) => Future[Unit]): String = {

    routes.find(r ⇒ r.uri.matchUri(uriFilter)) map { route ⇒

      val id = idCounter.incrementAndGet().toHexString
      val subscription = new Subscription[Unit, IN](1, /*todo: per topic thread count*/
        route, uriFilter, groupName, inputDeserializer, handler
      )
      subscriptions.put(id, subscription)
      subscription.run()
      id

    } getOrElse {
      throw new NoTransportRouteException(s"Kafka consumer (server). Uri: $uriFilter")
    }
  }

  override def off(subscriptionId: String): Unit = {
    subscriptions.get(subscriptionId).foreach { s ⇒
      s.stop(0.seconds)
      subscriptions.remove(subscriptionId)
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


  class Subscription[OUT, IN <: TransportRequest](
                                                   threadCount: Int,
                                                   route: KafkaRoute,
                                                   uriFilter: Uri,
                                                   groupName: String,
                                                   inputDeserializer: Deserializer[IN],
                                                   handler: (IN) ⇒ Future[OUT]) {

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
        val input = inputDeserializer(inputBytes) // todo: encoding!
        if (uriFilter.matchUri(input.uri)) { // todo: test order of matching?
          if (logMessages && log.isTraceEnabled) {
            log.trace(s"Consumer #$consumerId got message from partiton#${next.partition}: $messageString")
          }
          handler(input)
        } else {
          if (logMessages && log.isTraceEnabled) {
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
      log.info(s"Starting consumer #$consumerId on topic ${route.kafkaTopic} -> $uriFilter}")
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
