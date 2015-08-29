package eu.inn.hyperbus.serialization





case class DecodeException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)