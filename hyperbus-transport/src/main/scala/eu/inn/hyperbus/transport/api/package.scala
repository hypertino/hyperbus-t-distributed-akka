package eu.inn.hyperbus.transport

import java.io.{InputStream, OutputStream}

import scala.language.experimental.macros

package object api {
  type Serializer[-T] = Function2[T, OutputStream, Unit]
  type Deserializer[+T] = Function1[InputStream, T]
}
