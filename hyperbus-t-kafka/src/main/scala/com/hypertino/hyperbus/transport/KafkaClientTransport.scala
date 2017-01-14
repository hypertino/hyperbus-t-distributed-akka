package com.hypertino.hyperbus.transport

import java.util.Properties

import com.typesafe.config.Config
import com.hypertino.hyperbus.serialization.StringSerializer
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.kafkatransport.{ConfigLoader, KafkaPartitionKeyIsNotDefined, KafkaRoute}
import com.hypertino.hyperbus.util.ConfigUtils._
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}





class KafkaClientTransport(producerProperties: Properties,
                           routes: List[KafkaRoute],
                           encoding: String = "UTF-8")
                          (implicit val executionContext: ExecutionContext) extends ClientTransport {

  def this(config: Config) = this(
    producerProperties = ConfigLoader.loadProducerProperties(config.getConfig("producer")),
    routes = ConfigLoader.loadRoutes(config.getConfigList("routes")),
    encoding = config.getOptionString("encoding").getOrElse("UTF-8")
  )(scala.concurrent.ExecutionContext.global) // todo: configurable ExecutionContext like in akka?

  protected[this] val log = LoggerFactory.getLogger(this.getClass)
  protected[this] val producer = new KafkaProducer[String, String](producerProperties)

  override def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse] = ???

  override def publish(message: TransportRequest): Future[PublishResult] = {
    routes.find(r ⇒ r.requestMatcher.matchMessage(message)) map (publishToRoute(_, message)) getOrElse {
      throw new NoTransportRouteException(s"Kafka producer (client). Uri: ${message.uri}")
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    Future {
      producer.close()
      true
    } recover {
      case e: Throwable ⇒
        log.error("Can't close kafka producer", e)
        false
    }
  }

  private def publishToRoute(route: KafkaRoute, message: TransportRequest): Future[PublishResult] = {
    val messageString = StringSerializer.serializeToString(message)

    val record: ProducerRecord[String, String] =
      if (route.kafkaPartitionKeys.isEmpty) {
        // no partition key
        new ProducerRecord(route.kafkaTopic, messageString)
      }
      else {
        val recordKey = route.kafkaPartitionKeys.map { key: String ⇒ // todo: check partition key logic
          message.uri.args.getOrElse(key,
            throw new KafkaPartitionKeyIsNotDefined(s"Argument $key is not defined for ${message.uri}")
          ).specific
        }.foldLeft("")(_ + "," + _.replace("\\", "\\\\").replace(",", "\\,"))

        new ProducerRecord(route.kafkaTopic, recordKey.substring(1), messageString)
      }

    val promise = Promise[PublishResult]()

    producer.send(record, new Callback {
      def onCompletion(recordMetadata: RecordMetadata, e: Exception): Unit = {
        if (e != null) {
          promise.failure(e)
          log.error(s"Can't send to kafka. ${route.kafkaTopic} ${if (record.key() != null) "/" + record.key} : $message", e)
        }
        else {
          if (log.isTraceEnabled) {
            log.trace(s"Message $message is published to ${recordMetadata.topic()} ${if (record.key() != null) "/" + record.key}: ${recordMetadata.partition()}/${recordMetadata.offset()}")
          }
          promise.success(
            new PublishResult {
              def sent = Some(true)
              def offset = Some(s"${recordMetadata.partition()}/${recordMetadata.offset()}}")
              override def toString = s"PublishResult(sent=$sent,offset=$offset)"
            }
          )
        }
      }
    })
    promise.future
  }
}
