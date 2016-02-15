package eu.inn.hyperbus.model.serialization.util

import java.io.ByteArrayInputStream

import eu.inn.binders.dynamic.{Value, Null}
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard.{StandardResponse, ErrorBody}
import eu.inn.hyperbus.serialization.MessageDeserializer

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

object StringDeserializer {
  val defaultEncoding = "UTF-8"
  
  def request[T <: Request[_]](input: String, encoding: String): T = macro StringDeserializerImpl.request[T]
  def request[T <: Request[_]](input: String): T = macro StringDeserializerImpl.requestUtf8[T]

  def dynamicRequest(input: String): DynamicRequest = dynamicRequest(input, defaultEncoding)
  def dynamicRequest(input: String, encoding: String): DynamicRequest = {
    val is = new java.io.ByteArrayInputStream(input.getBytes(encoding))
    eu.inn.hyperbus.serialization.MessageDeserializer.deserializeRequestWith[DynamicRequest](is)(DynamicRequest.apply)
  }

  def dynamicBody(content: Option[String]) : DynamicBody = dynamicBody(content, defaultEncoding)
  def dynamicBody(content: Option[String], encoding: String) : DynamicBody = content match {
    case None ⇒ DynamicBody(Null)
    case Some(string) ⇒ {
      import eu.inn.binders._
      implicit val jsf = new eu.inn.hyperbus.serialization.JsonHalSerializerFactory[eu.inn.binders.naming.PlainConverter]
      val value = eu.inn.binders.json.SerializerFactory.findFactory().withStringParser(string) { case jsonParser ⇒
        import eu.inn.hyperbus.serialization.MessageSerializer.bindOptions // dont remove this!
        jsonParser.unbind[Value]
      }
      DynamicBody(value)
    }
  }

  // todo: response for DefinedResponse

  def dynamicResponse(input: String): Response[Body] = dynamicResponse(input, defaultEncoding)
  def dynamicResponse(input: String, encoding: String): Response[Body] = {
    val byteStream = new ByteArrayInputStream(input.getBytes(encoding))
    MessageDeserializer.deserializeResponseWith(byteStream) { (responseHeader, responseBodyJson) =>
      StandardResponse(responseHeader, responseBodyJson)
    }
  }
}

private [util] object StringDeserializerImpl {
  def request[T: c.WeakTypeTag](c: Context)(input: c.Expr[String], encoding: c.Expr[String]): c.Expr[T] = {
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

  def requestUtf8[T: c.WeakTypeTag](c: Context)(input: c.Expr[String]): c.Expr[T] = {
    import c.universe._
    request[T](c)(input, c.Expr[String](Literal(Constant(StringDeserializer.defaultEncoding))))
  }
}
