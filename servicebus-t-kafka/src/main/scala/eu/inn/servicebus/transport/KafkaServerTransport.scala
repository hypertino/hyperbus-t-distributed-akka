package eu.inn.servicebus.transport

import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

import com.typesafe.config.Config
import eu.inn.servicebus.serialization.{Encoder, PartitionArgsExtractor, Decoder}
import eu.inn.servicebus.transport.kafka.ConfigLoader
import org.apache.kafka.clients.consumer.{KafkaConsumer, Consumer, ConsumerConfig}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class KafkaServerTransport(consumerProperties: Properties) extends ServerTransport {
  def this(config: Config) = this(
    ConfigLoader.loadProperties(config.getConfig("consumer"))
  )

  protected [this] val subscriptions = new TrieMap[String, Subscription[_, _]]
  protected [this] val idCounter = new AtomicLong(0)
  protected [this] val consumer = new KafkaConsumer[String,String](consumerProperties)

  override def process[OUT, IN](topic: Topic, inputDecoder: Decoder[IN], partitionArgsExtractor: PartitionArgsExtractor[IN], exceptionEncoder: Encoder[Throwable])(handler: (IN) ⇒ SubscriptionHandlerResult[OUT]): String = ???

  override def subscribe[IN](topic: Topic, groupName: String, inputDecoder: Decoder[IN],
                             partitionArgsExtractor: PartitionArgsExtractor[IN])
                            (handler: (IN) ⇒ SubscriptionHandlerResult[Unit]): String = {
    val id = idCounter.incrementAndGet().toHexString
    val subscription = new Subscription[Unit,IN](1, /*todo: per topic thread count*/
      topic, groupName, inputDecoder, partitionArgsExtractor, handler
    )
    subscriptions.put(id, subscription)
    subscription.run
    id
  }

  override def off(subscriptionId: String): Unit = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???


  class Subscription[OUT, IN](
                               threadCount: Int,
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

    private def consume(): Unit = {
      consumer.subscribe()
      val stream = consumer.metrics()
    }
  }
}
