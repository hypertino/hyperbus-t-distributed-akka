package eu.inn.hyperbus.util

import org.slf4j.{Logger, MDC}

object LogUtils {
  implicit class ExtendConfig(log: Logger) {
    def trace(mdc: Map[String,Any], s: String) = {
      mdc.foreach(kv ⇒ MDC.put(kv._1, kv._2.toString))
      log.trace(s)
      MDC.clear()
    }
    def debug(mdc: Map[String,Any], s: String) = {
      mdc.foreach(kv ⇒ MDC.put(kv._1, kv._2.toString))
      log.debug(s)
      MDC.clear()
    }
    def info(mdc: Map[String,Any], s: String) = {
      mdc.foreach(kv ⇒ MDC.put(kv._1, kv._2.toString))
      log.info(s)
      MDC.clear()
    }
    def warn(mdc: Map[String,Any], s: String) = {
      mdc.foreach(kv ⇒ MDC.put(kv._1, kv._2.toString))
      log.warn(s)
      MDC.clear()
    }
    def error(mdc: Map[String,Any], s: String, exception: Throwable = null) = {
      mdc.foreach(kv ⇒ MDC.put(kv._1, kv._2.toString))
      if (exception != null)
        log.error(s, exception)
      else
        log.error(s)
      MDC.clear()
    }
  }
}
