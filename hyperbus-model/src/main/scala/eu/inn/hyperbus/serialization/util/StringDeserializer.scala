package eu.inn.hyperbus.serialization.util

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

object StringDeserializer {
  def deserializeFromString[T](input: String, encoding: String): T = macro StringDeserializerImpl.deserializeFromString[T]
  def deserializeFromString[T](input: String): T = macro StringDeserializerImpl.deserializeFromStringUtf8[T]
}

private [util] object StringDeserializerImpl {
  def deserializeFromString[T: c.WeakTypeTag](c: Context)(input: c.Expr[String], encoding: c.Expr[String]): c.Expr[T] = {
    import c.universe._
    val isVal = newTermName(c.fresh("is"))
    val typ = weakTypeOf[T]
    val deserializer = typ.companion.declaration(TermName("apply"))
    if (typ.companion == null) {
      c.abort(c.enclosingPosition, s"Can't find companion object for $typ (required to deserialize)")
    }
    c.Expr[T](q"""{
      val $isVal = new java.io.ByteArrayInputStream($input.getBytes($encoding))
      eu.inn.hyperbus.serialization.MessageDeserializer.deserializeRequestWith[$typ]($isVal)($deserializer)
    }""")
  }

  def deserializeFromStringUtf8[T: c.WeakTypeTag](c: Context)(input: c.Expr[String]): c.Expr[T] = {
    import c.universe._
    deserializeFromString[T](c)(input, c.Expr[String](Literal(Constant("UTF-8"))))
  }
}
