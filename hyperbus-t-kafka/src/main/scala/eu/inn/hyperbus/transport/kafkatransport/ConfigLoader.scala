package eu.inn.hyperbus.transport.kafkatransport

import java.util.Properties

import com.typesafe.config.Config
import eu.inn.hyperbus.transport._
import eu.inn.hyperbus.transport.api.matchers.AnyValue
import eu.inn.hyperbus.transport.api.uri.{Uri}

object ConfigLoader {

  import scala.collection.JavaConversions._

  def loadRoutes(routesConfigList: java.util.List[_ <: Config]): List[KafkaRoute] = {
    import eu.inn.binders.tconfig._

    routesConfigList.map { config ⇒
      val kafkaTopic = config.read[KafkaTopicPojo]("kafka")
      val uri = if (config.hasPath("uri"))
        Uri(config.getValue("uri"))
      else
        Uri(AnyValue)
      KafkaRoute(uri, kafkaTopic.topic, kafkaTopic.partitionKeys.getOrElse(List.empty))
    }.toList
  }

  def loadConsumerProperties(config: Config) = loadProperties(config, defaultConsumerProperties)

  def loadProducerProperties(config: Config) = loadProperties(config, defaultProducerProperties)

  private def loadProperties(config: Config, defaultProperties: Map[String, String]) = {
    val properties = new Properties()
    config.entrySet().map { entry ⇒
      properties.setProperty(entry.getKey, entry.getValue.unwrapped().toString)
    }
    defaultProperties.foreach(kv ⇒
      if (properties.getProperty(kv._1) == null)
        properties.setProperty(kv._1, kv._2)
    )
    properties
  }

  private val defaultConsumerProperties = Map[String, String](
    "key.deserializer" → "org.apache.kafka.common.serialization.StringDeserializer",
    "value.deserializer" → "org.apache.kafka.common.serialization.StringDeserializer",
    "partition.assignment.strategy" → "range"
  )

  private val defaultProducerProperties = Map[String, String](
    "key.serializer" → "org.apache.kafka.common.serialization.StringSerializer",
    "value.serializer" → "org.apache.kafka.common.serialization.StringSerializer"
  )
}

private [kafkatransport] case class KafkaTopicPojo(topic: String, partitionKeys: Option[List[String]])