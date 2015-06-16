package eu.inn.servicebus.util

import java.util.concurrent.TimeUnit

import com.typesafe.config.{ConfigObject, Config}

object ConfigUtils {
  import scala.collection.JavaConversions._

  implicit class ExtendConfig(config: Config) {
    def getOptionString(key: String): Option[String] =
      if (hasNot(key)) None else Some(config.getString(key))

    def getString(key: String, default: String): String =
      if (hasNot(key)) default else config.getString(key)

    def getOptionConfig(key: String): Option[Config] =
      if (hasNot(key))  None else Some(config.getConfig(key))

    def getOptionObject(key: String): Option[ConfigObject] =
      if (hasNot(key))  None else Some(config.getObject(key))

    def getOptionList(key: String): Option[Seq[Config]] =
      if (hasNot(key))  None else Some(config.getConfigList(key).toSeq)

    def getOptionBoolean(key: String): Option[Boolean] =
      if (hasNot(key))  None else Some(config.getBoolean(key))

    def getOptionLong(key: String): Option[Long] =
      if (hasNot(key))  None else Some(config.getLong(key))

    def getOptionDuration(key: String): Option[scala.concurrent.duration.FiniteDuration] =
      if (hasNot(key)) None else Some(
        scala.concurrent.duration.Duration(config.getDuration(key).toMillis, TimeUnit.MILLISECONDS)
      )
    
    def hasNot(key: String) = !config.hasPath(key) || config.getIsNull(key)
  }
}
