package eu.inn.servicebus.transport

import java.io.ByteArrayOutputStream
import java.util.Properties

import com.typesafe.config.Config
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import eu.inn.servicebus.transport.kafkatransport.ConfigLoader
import org.apache.kafka.clients.producer.{RecordMetadata, Callback, ProducerRecord, KafkaProducer}
import org.slf4j.LoggerFactory

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.FiniteDuration
import eu.inn.servicebus.util.ConfigUtils._

case class KafkaRoute(urlArg: Filter,
                     partitionArgs: Filters = Filters.empty,
                     targetTopic: String = "hyperbus",
                     targetPartitionArgs: List[String] = List.empty)

class KafkaPartitionArgIsNotDefined(message: String) extends RuntimeException(message)

class KafkaClientTransport(producerProperties: Properties,
                          routes: List[KafkaRoute],
                          logMessages: Boolean = false,
                          encoding: String = "UTF-8") extends ClientTransport {

  def this(config: Config) = this(
    producerProperties = ConfigLoader.loadProducerProperties(config.getConfig("producer")),
    routes = ConfigLoader.loadRoutes(config.getList("routes")),
    logMessages = config.getOptionBoolean("log-messages") getOrElse false,
    encoding = config.getOptionString("encoding").getOrElse("UTF-8")
  )

  protected [this] val log = LoggerFactory.getLogger(this.getClass)
  protected [this] val producer = new KafkaProducer[String,String](producerProperties)

  override def ask[OUT, IN](topic: Topic, message: IN, inputEncoder: Encoder[IN], outputDecoder: Decoder[OUT]): Future[OUT] = ???

  override def publish[IN](topic: Topic, message: IN, inputEncoder: Encoder[IN]): Future[Unit] = {

    routes.find(r ⇒ r.urlArg.matchArg(topic.url) &&
      r.partitionArgs.matchArgs(topic.values)) map (publishToRoute(_, topic, message, inputEncoder)) getOrElse {
      throw new NoTransportRouteException(s"Kafka producer (client). Topic: ${topic.url}/${topic.values.toString}")
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future {
      producer.close()
      true
    } recover {
      case e: Throwable ⇒
        log.error("Can't close kafka producer", e)
        false
    }
  }

  private def publishToRoute[IN](route: KafkaRoute, topic: Topic, message: IN, inputEncoder: Encoder[IN]): Future[Unit] = {
    val inputBytes = new ByteArrayOutputStream()
    inputEncoder(message, inputBytes)
    val messageString = inputBytes.toString(encoding)

    val record: ProducerRecord[String,String] =
      if (route.targetPartitionArgs.isEmpty) { // no partition key
        new ProducerRecord(route.targetTopic, messageString)
      }
      else {
        val recordKey = route.targetPartitionArgs.map { key: String ⇒
          topic.values.getOrElse(key, throw new KafkaPartitionArgIsNotDefined(s"PartitionArg $key is not defined in $topic"))
        }.foldLeft("")(_+_)

        new ProducerRecord(route.targetTopic, recordKey, messageString)
      }

    if (logMessages && log.isTraceEnabled) {
      log.trace(s"Sending to kafka. ${route.targetTopic} ${if (record.key() != null) "/" + record.key} : #${message.hashCode()} $message")
    }

    val promise = Promise[Unit]()

    producer.send(record, new Callback {
      def onCompletion(recordMetadata: RecordMetadata, e: Exception): Unit = {
        if (e != null) {
          promise.failure(e)
          log.error(s"Can't send to kafka. ${route.targetTopic} ${if (record.key() != null) "/" + record.key} : $message", e)
        }
        else {
          promise.success({})
          if (logMessages && log.isTraceEnabled) {
            log.trace(s"Sent to kafka. ${route.targetTopic} ${if (record.key() != null) "/" + record.key} : #${message.hashCode()}." +
              s"Offset: ${recordMetadata.offset()} partition: ${recordMetadata.partition()}")
          }
        }
      }
    })
    promise.future
  }
}
