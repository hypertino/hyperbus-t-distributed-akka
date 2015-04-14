package eu.inn.hyperbus.serialization

trait Decoder[T] {
  def decode(s: String): T
}
