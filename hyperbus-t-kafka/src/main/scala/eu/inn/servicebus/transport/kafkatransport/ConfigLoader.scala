package eu.inn.servicebus.transport.kafkatransport

import java.util.Properties

import com.typesafe.config.{ConfigValue, ConfigObject, Config}
import eu.inn.servicebus.transport._
import eu.inn.servicebus.transport.config.{TransportRouteHolder, TransportConfigurationLoader}
import eu.inn.servicebus.util.ConfigUtils
import org.apache.kafka.clients.producer.KafkaProducer

case class TopicInfoHolder(topic: Option[String], partitionKeys: Option[List[String]])

object ConfigLoader {
  import scala.collection.JavaConversions._
  import ConfigUtils._

  def loadRoutes(routesConfigList: java.util.List[_ <: ConfigValue]): List[KafkaRoute] = {
    import eu.inn.binders.tconfig._

    routesConfigList.map { config ⇒
      //val urlArg = TransportConfigurationLoader.getPartitionArg(config.getOptionString("url"), config.getOptionString("match-type"))
      //val partitionArgs = TransportConfigurationLoader.readPartitionArgs(config)
      val th = config.read[TransportRouteHolder]
      val ti = config.read[TopicInfoHolder]
      val topic = ti.topic.getOrElse("hyperbus")
      val partitionKeys = ti.partitionKeys.getOrElse(List.empty)
      val urlArg = TransportConfigurationLoader.getPartitionArg(th.url, th.matchType)
      KafkaRoute(urlArg, th.partitionArgsN, topic, partitionKeys)
    }.toList
  }

  def loadConsumerProperties(config: Config) = loadProperties(config, defaultConsumerProperties)

  def loadProducerProperties(config: Config) = loadProperties(config, defaultProducerProperties)

  private def loadProperties(config: Config, defaultProperties: Map[String,String]) = {
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

  private val defaultConsumerProperties = Map[String,String](
    "key.deserializer" → "org.apache.kafka.common.serialization.StringDeserializer",
    "value.deserializer" → "org.apache.kafka.common.serialization.StringDeserializer",
    "partition.assignment.strategy" → "range"
  )

  private val defaultProducerProperties = Map[String,String](
    "key.serializer" → "org.apache.kafka.common.serialization.StringSerializer",
    "value.serializer" → "org.apache.kafka.common.serialization.StringSerializer"
  )
}



/*
object ServiceBusConfigurationLoader {
  import scala.collection.JavaConversions._
  import ConfigUtils._

  def fromConfig(config: Config): ServiceBusConfiguration = {
    val sc = config.getConfig("service-bus")

    val st = sc.getObject("transports")
    val transportMap = st.entrySet().map { entry ⇒
      val transportTag = entry.getKey
      val transportConfig = sc.getConfig("transports."+transportTag)
      val transport = createTransport(transportConfig)
      transportTag → transport
    }.toMap

    ServiceBusConfiguration(
      sc.getConfigList("client-routes").map{ li⇒
        getTransportRoute[ClientTransport](transportMap, li)
      }.toSeq,
      sc.getConfigList("server-routes").map{ li⇒
        getTransportRoute[ServerTransport](transportMap, li)
      }.toSeq
    )
  }

  private def getTransportRoute[T](transportMap: Map[String, Any], config: Config): TransportRoute[T] = {
    val transportName = config.getString("transport")
    val transport = transportMap.getOrElse(transportName,
      throw new ServiceBusConfigurationError(s"Couldn't find transport '$transportName' at ${config.origin()}")
    ).asInstanceOf[T]

    val urlArg = getPartitionArg(config.getOptionString("url").getOrElse(""), config.getOptionString("match-type"))

    val partitionArgs = config.getOptionObject("partition-args").map { c⇒
      readPartitionArgs(c, config)
    } getOrElse {
      PartitionArgs(Map.empty)
    }

    TransportRoute[T](transport, urlArg, partitionArgs)
  }

  private def createTransport(config: Config): Any = {
    val className = {
      val s = config.getString("class-name")
      if (s.contains("."))
        s
      else
        "eu.inn.servicebus.transport." + s
    }
    val clazz = Class.forName(className)
    val transportConfig = config.getOptionConfig("configuration").getOrElse(
      ConfigFactory.parseString("")
    )
    clazz.getConstructor(classOf[Config]).newInstance(transportConfig)
  }

  private def getPartitionArg(value: String, matchType: Option[String]) = matchType match {
    case Some("Any") ⇒ AnyArg
    case Some("Exact") ⇒ ExactArg(value)
    case Some("Regex") ⇒ RegexArg(value)
    case _ ⇒ ExactArg(value)
  }

  private def readPartitionArgs(config: ConfigObject, parent: Config) = PartitionArgs(
    config.toMap.keys.map { key ⇒
      val c = parent.getConfig(s"partition-args.$key")
      val value = c.getOptionString("value").getOrElse("")
      val matchType = c.getOptionString("match-type")
      (key, getPartitionArg(value, matchType))
    }.toMap
  )
}
*/