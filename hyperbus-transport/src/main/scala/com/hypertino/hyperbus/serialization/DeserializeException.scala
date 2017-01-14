package com.hypertino.hyperbus.serialization

case class DeserializeException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)