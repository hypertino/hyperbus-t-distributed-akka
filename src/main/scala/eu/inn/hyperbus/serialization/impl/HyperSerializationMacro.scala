package eu.inn.hyperbus.serialization.impl

import eu.inn.servicebus.serialization.{Decoder, Encoder}
import scala.reflect.macros.blackbox.Context

private[hyperbus] object HyperSerializationMacro {
  def createEncoder[T: c.WeakTypeTag](c: Context): c.Expr[Encoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]
    val obj = q"""
      new Object with eu.inn.servicebus.serialization.Encoder[$t] {
        import eu.inn.binders.json._
        def encode(t: $t) = eu.inn.hyperbus.serialization.impl.Helpers.toJson(t, t.body.toJson)
      }
    """
    //println(obj)
    c.Expr[Encoder[T]](obj)
  }

  def createDecoder[T: c.WeakTypeTag](c: Context): c.Expr[Decoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]
    val tBody = extractTypeArgs(c)(t).head// sweakTypeOf[B]//t.dealias.typeArgs.head

    val obj = q"""
      val bodyDecoder = eu.inn.servicebus.serialization.JsonDecoder.createDecoder[$tBody]
      new Object with eu.inn.servicebus.serialization.Decoder[$t] {
        def decode(s: String) = eu.inn.hyperbus.serialization.impl.Helpers.parseJson[$tBody](s, bodyDecoder)
      }
    """
    //println(obj)
    c.Expr[Decoder[T]](obj)
  }

  protected def extractTypeArgs(c: Context)(tpe: c.Type): List[c.Tree] = {
    import c.universe._
    tpe match {
      case TypeRef(_, _, args) => args.map(TypeTree(_))
      case _ =>
        c.abort(c.enclosingPosition, s"Can't extract typeArgs from $tpe")
    }
  }
}

