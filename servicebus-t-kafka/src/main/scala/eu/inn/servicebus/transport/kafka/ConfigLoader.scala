package eu.inn.servicebus.transport.kafka

import java.util.Properties

import com.typesafe.config.{ConfigObject, Config}
import eu.inn.servicebus.ServiceBusConfigurationLoader
import eu.inn.servicebus.transport._
import eu.inn.servicebus.util.ConfigUtils
import org.apache.kafka.clients.producer.KafkaProducer

object ConfigLoader {
  import scala.collection.JavaConversions._
  import ConfigUtils._

  def loadRoutes(routesConfigList: java.util.List[_ <: Config]): List[KafkaRoute] = {
    routesConfigList.map { config ⇒
      val urlArg = ServiceBusConfigurationLoader.getPartitionArg(config.getOptionString("url").getOrElse(""), config.getOptionString("match-type"))
      val partitionArgs = ServiceBusConfigurationLoader.readPartitionArgs(config)
      val topic = config.getOptionString("topic").getOrElse("hyperbus")
      val partitionKeys = if (config.hasPath("partitionKeys"))
        config.getStringList("partitionKeys").toList
      else
        List.empty
      KafkaRoute(urlArg, partitionArgs,topic, partitionKeys)
    }.toList
  }

  def loadProperties(config: Config) = {
    val properties = new Properties()
    config.entrySet().map { entry ⇒
      properties.setProperty(entry.getKey, entry.getValue.unwrapped().toString)
    }
    if (properties.getProperty("key.serializer") == null)
      properties.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    if (properties.getProperty("value.serializer") == null)
      properties.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    properties
  }
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