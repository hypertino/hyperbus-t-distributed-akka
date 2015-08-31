package eu.inn.hyperbus.transport

import java.io.{InputStream, OutputStream}

import scala.language.experimental.macros

package object api {
  type Encoder[T] = Function2[T, OutputStream, Unit]  // todo: OutputStream -> String
  type Decoder[T] = Function1[InputStream, T]         // todo: InputStream -> String
}
