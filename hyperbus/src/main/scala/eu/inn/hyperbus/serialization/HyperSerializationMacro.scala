package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.protocol.{ErrorBody, DynamicBody, DynamicRequest, Message}
import eu.inn.servicebus.serialization.Encoder

import scala.reflect.macros.blackbox.Context

private[hyperbus] object HyperSerializationMacro {
  def createEncoder[T: c.WeakTypeTag](c: Context): c.Expr[Encoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]
    val tBody = t.baseType(typeOf[Message[_]].typeSymbol).typeArgs.head

    val obj = q"""
      (t: $t, out: java.io.OutputStream) => {
        val bodyEncoder = eu.inn.servicebus.serialization.createEncoder[$tBody]
        eu.inn.hyperbus.serialization.impl.Helpers.encodeMessage(t, bodyEncoder, out)
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
      (requestHeader: eu.inn.hyperbus.serialization.RequestHeader, requestBodyJson: com.fasterxml.jackson.core.JsonParser) => {
        ..$decoder
      }
    }"""
    //println(obj)
    c.Expr[RequestDecoder](obj)
  }

  def createResponseBodyDecoder[T: c.WeakTypeTag](c: Context): c.Expr[ResponseBodyDecoder] = {
    import c.universe._
    val t = weakTypeOf[T]

    val decoder =
      if (t <:< typeOf[DynamicBody]) {
        q"eu.inn.hyperbus.serialization.impl.Helpers.decodeDynamicResponseBody(responseHeader, responseBodyJson)"
      }
      else if (t <:< typeOf[ErrorBody]) {
        q"eu.inn.hyperbus.serialization.impl.Helpers.decodeErrorResponseBody(responseHeader, responseBodyJson)"
      }
      else {
        // todo: validate contentType?
        q"""
          eu.inn.binders.json.SerializerFactory.findFactory().withJsonParser(responseBodyJson) { deserializer =>
            deserializer.unbind[$t]
          }
        """
      }

    val obj = q"""{
      (responseHeader: eu.inn.hyperbus.serialization.ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser) => {
        ..$decoder
      }
    }"""
    //println(obj)
    c.Expr[ResponseBodyDecoder](obj)
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

