package eu.inn.hyperbus.serialization

/**
  * Created by maqdev on 25.02.16.
  */
class HeaderIsRequiredException(header: String) extends RuntimeException(s"Header value is not set: $header")
