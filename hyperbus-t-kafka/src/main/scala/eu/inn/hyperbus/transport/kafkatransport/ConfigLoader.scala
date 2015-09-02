package eu.inn.hyperbus.transport.kafkatransport

import java.util.Properties

import com.typesafe.config.{Config, ConfigValue}
import eu.inn.hyperbus.transport._
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportRouteHolder}

case class TopicInfoHolder(topic: Option[String], partitionKeys: Option[List[String]])

object ConfigLoader {

  import scala.collection.JavaConversions._

  def loadRoutes(routesConfigList: java.util.List[_ <: ConfigValue]): List[KafkaRoute] = {
    import eu.inn.binders.tconfig._

    routesConfigList.map { config ⇒
      //val urlArg = TransportConfigurationLoader.getPartitionArg(config.getOptionString("url"), config.getOptionString("match-type"))
      //val partitionArgs = TransportConfigurationLoader.readPartitionArgs(config)
      val th = config.read[TransportRouteHolder]
      val ti = config.read[TopicInfoHolder]
      val topic = ti.topic.getOrElse("hyperbus")
      val partitionKeys = ti.partitionKeys.getOrElse(List.empty)
      val urlArg = TransportConfigurationLoader.getFilter(th.url, th.matchType)
      KafkaRoute(urlArg, th.partitionArgsN, topic, partitionKeys)
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
