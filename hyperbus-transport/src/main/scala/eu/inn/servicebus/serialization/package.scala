package eu.inn.servicebus

import java.io.{InputStream, OutputStream}

import eu.inn.servicebus.transport.Filters

import scala.language.experimental.macros

package object serialization {
  type Encoder[T] = Function2[T, OutputStream, Unit]
  type Decoder[T] = Function1[InputStream, T]
  type FilterArgsExtractor[T] = Function1[T, Map[String,String]] // todo: rename

  // todo: move this to hyperbus
  def createDecoder[T]: Decoder[T] = macro JsonSerializationMacro.createDecoder[T]
  // todo: move this to hyperbus
  def createEncoder[T]: Encoder[T] = macro JsonSerializationMacro.createEncoder[T]
}
