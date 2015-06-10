package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.rest._
import eu.inn.servicebus.serialization._

import scala.reflect.macros.blackbox.Context

private[hyperbus] object HyperSerializationMacro {
  def createEncoder[T: c.WeakTypeTag](c: Context): c.Expr[Encoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]
    val tBody = t.baseType(typeOf[Message[_]].typeSymbol).typeArgs.head

    val bodyEncoder =
      if (tBody <:< typeOf[DynamicBody]) {
        q"eu.inn.hyperbus.serialization.impl.InnerHelpers.dynamicBodyEncoder _"
      }
      else if (tBody <:< typeOf[ErrorBody]) {
        q"eu.inn.hyperbus.serialization.impl.InnerHelpers.errorBodyEncoder _"
      }
      else if (tBody <:< typeOf[EmptyBody]) {
        q"eu.inn.hyperbus.serialization.impl.InnerHelpers.emptyBodyEncoder _"
      }
      else {
        q"eu.inn.servicebus.serialization.createEncoder[$tBody]"
      }

    val obj = q"""
      (t: $t, out: java.io.OutputStream) => {
        import eu.inn.hyperbus.serialization.impl.InnerHelpers.bindOptions
        val bodyEncoder = $bodyEncoder
        eu.inn.hyperbus.serialization.impl.InnerHelpers.encodeMessage(t, bodyEncoder, out)
      }
    """
    //println(obj)
    c.Expr[Encoder[T]](obj)
  }

  def createRequestDecoder[T <: Request[Body] : c.WeakTypeTag](c: Context): c.Expr[RequestDecoder[T]] = {
    import c.universe._
    val t = weakTypeOf[T]
    val tBody = t.baseType(typeOf[Message[_]].typeSymbol).typeArgs.head

    val decoder = if (t <:< typeOf[DynamicRequest]) {
      q"eu.inn.hyperbus.serialization.impl.InnerHelpers.decodeDynamicRequest(requestHeader, requestBodyJson)"
    } else {
      val to = t.typeSymbol.companion
      if (to == NoSymbol) {
        c.abort(c.enclosingPosition, s"$t doesn't have a companion object (it's not a case class)")
      }

      val bodyDecoder =
        if (tBody <:< typeOf[DynamicBody]) {
          q"eu.inn.hyperbus.serialization.impl.InnerHelpers.decodeDynamicBody(requestHeader, requestBodyJson)"
        }
        else if (tBody <:< typeOf[EmptyBody]) {
          q"eu.inn.hyperbus.serialization.impl.InnerHelpers.decodeEmptyBody(requestHeader, requestBodyJson)"
        }
        else {
          // todo: validate method & contentType?
          q"""
            eu.inn.binders.json.SerializerFactory.findFactory().withJsonParser(requestBodyJson) { deserializer =>
              deserializer.unbind[$tBody]
            }
          """
        }
      q"""
        val body = $bodyDecoder
        $to.${TermName("apply")}(body)
      """
    }

    val obj = q"""{
      (requestHeader: eu.inn.hyperbus.serialization.RequestHeader, requestBodyJson: com.fasterxml.jackson.core.JsonParser) => {
        ..$decoder
      }
    }"""
    //println(obj)
    c.Expr[RequestDecoder[T]](obj)
  }

  def createResponseBodyDecoder[T: c.WeakTypeTag](c: Context): c.Expr[ResponseBodyDecoder] = {
    import c.universe._
    val t = weakTypeOf[T]

    val decoder =
      if (t <:< typeOf[DynamicBody]) {
        q"eu.inn.hyperbus.serialization.impl.InnerHelpers.decodeDynamicResponseBody(responseHeader, responseBodyJson)"
      }
      else if (t <:< typeOf[EmptyBody]) {
        q"eu.inn.hyperbus.serialization.impl.InnerHelpers.decodeEmptyResponseBody(responseHeader, responseBodyJson)"
      }
      else if (t <:< typeOf[ErrorBody]) {
        q"eu.inn.hyperbus.serialization.impl.InnerHelpers.decodeErrorResponseBody(responseHeader, responseBodyJson)"
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

