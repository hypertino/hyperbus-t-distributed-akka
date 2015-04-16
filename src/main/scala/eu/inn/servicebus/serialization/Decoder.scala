package eu.inn.servicebus.serialization

trait Decoder[T] {
  def decode(s: String): T
}
