package eu.inn.hyperbus.transport

import java.io.ByteArrayInputStream
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

import com.typesafe.config.Config
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.kafkatransport.ConfigLoader
import eu.inn.hyperbus.util.ConfigUtils._
import kafka.consumer.{Consumer, ConsumerConfig, KafkaStream}
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

class KafkaServerTransport(
                            consumerProperties: Properties,
                            routes: List[KafkaRoute],
                            logMessages: Boolean = false,
                            encoding: String = "UTF-8") extends ServerTransport {
  def this(config: Config) = this(
    consumerProperties = ConfigLoader.loadConsumerProperties(config.getConfig("consumer")),
    routes = ConfigLoader.loadRoutes(config.getConfigList("routes")),
    logMessages = config.getOptionBoolean("log-messages") getOrElse false,
    encoding = config.getOptionString("encoding") getOrElse "UTF-8"
  )

  protected[this] val subscriptions = new TrieMap[String, Subscription[_, _]]
  protected[this] val idCounter = new AtomicLong(0)
  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  override def process[IN <: TransportRequest](topicFilter: Topic, inputDeserializer: Deserializer[IN], exceptionSerializer: Serializer[Throwable])
                                              (handler: (IN) => Future[TransportResponse]): String = ???

  override def subscribe[IN <: TransportRequest](topicFilter: Topic, groupName: String, inputDeserializer: Deserializer[IN])
                                                (handler: (IN) => Future[Unit]): String = {

    routes.find(r ⇒ r.topic.matchTopic(topicFilter)) map { route ⇒

      val id = idCounter.incrementAndGet().toHexString
      val subscription = new Subscription[Unit, IN](1, /*todo: per topic thread count*/
        route, topicFilter, groupName, inputDeserializer, handler
      )
      subscriptions.put(id, subscription)
      subscription.run()
      id

    } getOrElse {
      throw new NoTransportRouteException(s"Kafka consumer (server). Topic: $topicFilter")
    }
  }

  override def off(subscriptionId: String): Unit = {
    subscriptions.get(subscriptionId).foreach { s ⇒
      s.stop()
      subscriptions.remove(subscriptionId)
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    subscriptions.foreach { kv ⇒
      kv._2.stop()
    }
    subscriptions.clear()
    Future.successful(true) // todo: normal result
  }


  class Subscription[OUT, IN <: TransportRequest](
                                                   threadCount: Int,
                                                   route: KafkaRoute,
                                                   topicFilter: Topic,
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

    def run(): Unit = {
      val threadPool = Executors.newFixedThreadPool(threadCount) // todo: release on shutdown!!!!
      val consumerMap = consumer.createMessageStreams(Map(route.kafkaTopic → threadCount))
      val streams = consumerMap(route.kafkaTopic)

      streams.map { stream ⇒
        threadPool.submit(new Runnable {
          override def run(): Unit = consumeStream(stream)
        })
      }
    }

    def stop(): Unit = {
      consumer.commitOffsets
      consumer.shutdown()
    }

    def consumeMessage(consumerId: String, message: Array[Byte]): Unit = {
      lazy val messageString = new String(message, encoding)
      try {
        val inputBytes = new ByteArrayInputStream(message)
        val input = inputDeserializer(inputBytes) // todo: encoding!
        if (topicFilter.matchTopic(input.topic)) { // todo: test order of matching?
          if (logMessages && log.isTraceEnabled) {
            log.trace(s"Consumer #$consumerId got message: $messageString")
          }
          handler(input)
        } else {
          if (logMessages && log.isTraceEnabled) {
            log.trace(s"Consumer #$consumerId. Skipped message: $messageString")
          }
        }
      }
      catch {
        case NonFatal(e) ⇒
          log.error(s"Consumer #$consumerId can't deserialize message: $messageString", e)
      }
    }

    private def consumeStream(stream: KafkaStream[Array[Byte], Array[Byte]]): Unit = {
      val consumerId = Thread.currentThread().getName
      log.info(s"Starting consumer #$consumerId on topic ${route.kafkaTopic} -> $topicFilter}")
      try {
        val iterator = stream.iterator()
        while (iterator.hasNext()) {
          val message = iterator.next().message()
          consumeMessage(consumerId, message)
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
