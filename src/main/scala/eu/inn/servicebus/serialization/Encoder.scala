package eu.inn.servicebus.serialization

import java.io.OutputStream

trait Encoder[T] {
  def encode(t: T, out: OutputStream)
}
