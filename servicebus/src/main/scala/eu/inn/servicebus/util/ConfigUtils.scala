package eu.inn.servicebus.util

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.typesafe.config.{ConfigObject, Config}

object ConfigUtils {
  import scala.collection.JavaConversions._

  implicit class ExtendConfig(config: Config) {
    def getOptionString(key: String): Option[String] =
      if (hasNot(config,  key)) None else Some(config.getString(key))

    def getOptionConfig(key: String): Option[Config] =
      if (hasNot(config,  key))  None else Some(config.getConfig(key))

    def getOptionObject(key: String): Option[ConfigObject] =
      if (hasNot(config,  key))  None else Some(config.getObject(key))

    def getOptionList(key: String): Option[Seq[Config]] =
      if (hasNot(config,  key))  None else Some(config.getConfigList(key).toSeq)

    def getOptionBoolean(key: String): Option[Boolean] =
      if (hasNot(config,  key))  None else Some(config.getBoolean(key))

    def getOptionLong(key: String): Option[Long] =
      if (hasNot(config,  key))  None else Some(config.getLong(key))

    def getOptionDuration(key: String): Option[scala.concurrent.duration.FiniteDuration] =
      if (hasNot(config,  key)) None else Some(
        scala.concurrent.duration.Duration(config.getDuration(key).toMillis, TimeUnit.MILLISECONDS)
      )
    
    def hasNot(config: Config, key: String) = !config.hasPath(key) || config.getIsNull(key)
  }
}
