package eu.inn.hyperbus.serialization.impl

import eu.inn.hyperbus.protocol.{DynamicRequest, Message}
import eu.inn.hyperbus.serialization.RequestDecoder
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import scala.reflect.macros.blackbox.Context

private[hyperbus] object HyperSerializationMacro {
  // todo: is this really needed?
  def createEncoder[T: c.WeakTypeTag](c: Context): c.Expr[Encoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]
    val tBody = t.baseType(typeOf[Message[_]].typeSymbol).typeArgs.head

    val obj = q"""
      new eu.inn.servicebus.serialization.Encoder[$t] {
        import eu.inn.binders.json._
        def encode(t: $t, out: java.io.OutputStream) = {
          val bodyEncoder = eu.inn.servicebus.serialization.JsonEncoder.createEncoder[$tBody]
          eu.inn.hyperbus.serialization.impl.Helpers.encodeMessage(t, t.body, bodyEncoder, out)
        }
      }
    """
    //println(obj)
    c.Expr[Encoder[T]](obj)
  }

  def createRequestDecoder[T: c.WeakTypeTag](c: Context): c.Expr[RequestDecoder] = {
    import c.universe._
    val t = weakTypeOf[T]
    val tBody = t.baseType(typeOf[Message[_]].typeSymbol).typeArgs.head

    val decoder = if (t <:< typeOf[DynamicRequest]) {
      q"eu.inn.hyperbus.serialization.impl.Helpers.decodeDynamicRequest(requestHeader, requestBodyJson)"
    } else {
      val to = t.typeSymbol.companion
      if (to == NoSymbol) {
        c.abort(c.enclosingPosition, s"$t doesn't have a companion object (it's not a case class)")
      }
      // todo: validate method & contentType?
      q"""
        val body = eu.inn.binders.json.SerializerFactory.findFactory().withJsonParser(requestBodyJson) { deserializer =>
          deserializer.unbind[$tBody]
        }
        $to.${TermName("apply")}(body)
      """
    }

    val obj = q"""{
      import eu.inn.hyperbus.serialization._
      new RequestDecoder {
        def decode(requestHeader: RequestHeader, requestBodyJson: com.fasterxml.jackson.core.JsonParser): Request[Body] = {
          ..$decoder
        }
      }
    }"""
    //println(obj)
    c.Expr[RequestDecoder](obj)
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

