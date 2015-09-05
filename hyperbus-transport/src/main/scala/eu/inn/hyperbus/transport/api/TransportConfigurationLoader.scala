package eu.inn.hyperbus.transport.api

import com.typesafe.config.{ConfigValue, Config, ConfigFactory}
import eu.inn.hyperbus.util.ConfigUtils

class TransportConfigurationError(message: String) extends RuntimeException(message)

object TransportConfigurationLoader {

  import ConfigUtils._

  import scala.collection.JavaConversions._

  def fromConfig(config: Config): TransportConfiguration = {
    val sc = config.getConfig("service-bus")

    val st = sc.getObject("transports")
    val transportMap = st.entrySet().map { entry ⇒
      val transportTag = entry.getKey
      val transportConfig = sc.getConfig("transports." + transportTag)
      val transport = createTransport(transportConfig)
      transportTag → transport
    }.toMap

    import eu.inn.binders.tconfig._

    TransportConfiguration(
      sc.getConfigList("client-routes").map { li ⇒
        val transportName = li.read[String]("transport")
        getTransportRoute[ClientTransport](transportName, transportMap, li)
      }.toSeq,
      sc.getConfigList("server-routes").map { li ⇒
        val transportName = li.read[String]("transport")
        getTransportRoute[ServerTransport](transportName, transportMap, li)
      }.toSeq
    )
  }

  private def getTransportRoute[T](transportName: String, transportMap: Map[String, Any], config: Config): TransportRoute[T] = {
    val transport = transportMap.getOrElse(transportName,
      throw new TransportConfigurationError(s"Couldn't find transport '$transportName'")
    ).asInstanceOf[T]

    val topic = if (config.hasPath("topic"))
      Topic(config.getValue("topic"))
    else
      Topic(AnyValue)
    TransportRoute[T](transport, topic)
  }

  private def createTransport(config: Config): Any = {
    val className = {
      val s = config.getString("class-name")
      if (s.contains("."))
        s
      else
        "eu.inn.hyperbus.transport." + s
    }
    val clazz = Class.forName(className)
    val transportConfig = config.getOptionConfig("configuration").getOrElse(
      ConfigFactory.parseString("")
    )
    clazz.getConstructor(classOf[Config]).newInstance(transportConfig)
  }
}
