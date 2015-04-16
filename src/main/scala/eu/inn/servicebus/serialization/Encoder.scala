package eu.inn.servicebus.serialization

trait Encoder[T] {
  def encode(t: T): String
}
