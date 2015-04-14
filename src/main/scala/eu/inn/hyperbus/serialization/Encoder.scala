package eu.inn.hyperbus.serialization

trait Encoder[T] {
  def encode(t: T): String
}
