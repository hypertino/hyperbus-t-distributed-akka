package eu.inn.servicebus.transport

import java.io.ByteArrayInputStream
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

import com.typesafe.config.Config
import eu.inn.servicebus.serialization.{Encoder, PartitionArgsExtractor, Decoder}
import eu.inn.servicebus.transport.kafka.ConfigLoader
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer, Consumer, ConsumerConfig}
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import eu.inn.servicebus.util.ConfigUtils._
import scala.collection.JavaConversions._

import scala.util.control.NonFatal

class KafkaServerTransport(
                            consumerProperties: Properties,
                            routes: List[KafkaRoute],
                            logMessages: Boolean = false,
                            encoding: String = "UTF-8"
                            ) extends ServerTransport {
  def this(config: Config) = this(
    consumerProperties = ConfigLoader.loadProperties(config.getConfig("consumer")),
    routes = ConfigLoader.loadRoutes(config.getConfigList("routes")),
    logMessages = config.getOptionBoolean("log-messages") getOrElse false,
    encoding = config.getOptionString("encoding").getOrElse("UTF-8")
  )

  protected [this] val subscriptions = new TrieMap[String, Subscription[_, _]]
  protected [this] val idCounter = new AtomicLong(0)
  protected [this] val log = LoggerFactory.getLogger(this.getClass)

  override def process[OUT, IN](topic: Topic, inputDecoder: Decoder[IN], partitionArgsExtractor: PartitionArgsExtractor[IN], exceptionEncoder: Encoder[Throwable])(handler: (IN) ⇒ SubscriptionHandlerResult[OUT]): String = ???

  override def subscribe[IN](topic: Topic, groupName: String, inputDecoder: Decoder[IN],
                             partitionArgsExtractor: PartitionArgsExtractor[IN])
                            (handler: (IN) ⇒ SubscriptionHandlerResult[Unit]): String = {

    routes.find(r ⇒ r.urlArg.matchArg(ExactArg(topic.url)) &&
      r.partitionArgs.matchArgs(topic.partitionArgs)) map { route ⇒

      val id = idCounter.incrementAndGet().toHexString
      val subscription = new Subscription[Unit,IN](1, /*todo: per topic thread count*/
        route, topic, groupName, inputDecoder, partitionArgsExtractor, handler
      )
      subscriptions.put(id, subscription)
      subscription.run
      id

    } getOrElse {
      throw new NoTransportRouteException(s"Kafka consumer (server). Topic: ${topic.url}/${topic.partitionArgs.toString}")
    }
  }

  override def off(subscriptionId: String): Unit = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???


  class Subscription[OUT, IN](
                               threadCount: Int,
                               route: KafkaRoute,
                               topic: Topic,
                               groupName: String,
                               inputDecoder: Decoder[IN],
                               partitionArgsExtractor: PartitionArgsExtractor[IN],
                               handler: (IN) ⇒ SubscriptionHandlerResult[Unit]) {

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
      new KafkaConsumer[String,String](props)
    }

    def run(): Unit = {
      val threadPool = Executors.newFixedThreadPool(threadCount)
      for (i ← 1 to threadCount) {
        threadPool.submit(new Runnable {
          override def run(): Unit = consume()
        })
      }
    }

    def stop(): Unit = {
      consumer.close()
    }

    def consumeMessage(consumerId: String, message: ConsumerRecord[String, String]): Unit = {
      val messageString = message.value()
      try {
        val inputBytes = new ByteArrayInputStream(messageString.getBytes(encoding))
        val input = inputDecoder(inputBytes)
        val partitionArgs = partitionArgsExtractor(input)
        if (topic.partitionArgs.matchArgs(partitionArgs)) { // todo: !important! also need to check topic url!!!!
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

    private def consume(): Unit = {
      val consumerId = Thread.currentThread().getId.toHexString
      log.info(s"Starting consumer #$consumerId on topic ${route.targetTopic} -> $topic}")
      try {
        consumer.subscribe(route.targetTopic)
        try {
          var continue = true
          while (continue) {
            consumer.poll(0).toMap.get(route.targetTopic).map { messages ⇒
              if (!messages.records().isEmpty) {
                messages.records().map { message ⇒
                  consumeMessage(consumerId, message)
                }
              } else {
                continue = false
              }
            } getOrElse {
              continue = false
            }
          }
          log.info(s"Stopping consumer #$consumerId on topic ${route.targetTopic}")
        } finally {
          consumer.unsubscribe(route.targetTopic)
        }
      }
      catch {
        case NonFatal(t) ⇒
          log.error(s"Consumer #$consumerId failed", t)
      }
    }
  }
}
