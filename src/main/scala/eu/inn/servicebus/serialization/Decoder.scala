package eu.inn.servicebus.serialization

import java.io.InputStream

trait Decoder[T] {
  def decode(in: InputStream): T
}
