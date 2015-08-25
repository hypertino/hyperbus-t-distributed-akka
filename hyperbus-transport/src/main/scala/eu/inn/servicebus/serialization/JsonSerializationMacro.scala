package eu.inn.servicebus.serialization

import scala.reflect.macros.blackbox.Context

private[servicebus] object JsonSerializationMacro {
  def createEncoder[T: c.WeakTypeTag](c: Context): c.Expr[Encoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]

    val obj = q"""
      (t: $t, out: java.io.OutputStream) => {
        import eu.inn.binders.json._
        SerializerFactory.findFactory().withStreamGenerator(out) { serializer=>
          serializer.bind[${weakTypeOf[T]}](t)
        }
      }
    """
    //println(obj)
    c.Expr[Encoder[T]](obj)
  }

  def createDecoder[T: c.WeakTypeTag](c: Context): c.Expr[Decoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]

    val obj = q"""
      (in: java.io.InputStream) => {
        import eu.inn.binders.json._
        SerializerFactory.findFactory().withStreamParser[$t](in) { deserializer=>
          deserializer.unbind[$t]
        }
      }
    """
    //println(obj)
    c.Expr[Decoder[T]](obj)
  }
}
