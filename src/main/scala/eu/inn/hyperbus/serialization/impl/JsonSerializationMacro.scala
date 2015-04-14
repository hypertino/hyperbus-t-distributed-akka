package eu.inn.hyperbus.serialization.impl

import eu.inn.hyperbus.serialization.{Decoder, Encoder}

import scala.reflect.macros.blackbox.Context

object JsonSerializationMacro {
  def createEncoder[T: c.WeakTypeTag](c: Context): c.Expr[Encoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]

    val obj = q"""
      new Object with eu.inn.hyperbus.serialization.Encoder[$t] {
        import eu.inn.binders.json._
        def encode(t: $t) = t.toJson
      }
    """
    //println(obj)
    c.Expr[Encoder[T]](obj)
  }

  def createDecoder[T: c.WeakTypeTag](c: Context): c.Expr[Decoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]

    val obj = q"""
      new Object with eu.inn.hyperbus.serialization.Decoder[$t] {
        import eu.inn.binders.json._
        def decode(s: String) = s.parseJson[$t]
      }
    """
    //println(obj)
    c.Expr[Decoder[T]](obj)
  }
}
