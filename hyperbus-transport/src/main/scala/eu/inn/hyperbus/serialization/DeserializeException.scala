package eu.inn.hyperbus.serialization

case class DeserializeException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)