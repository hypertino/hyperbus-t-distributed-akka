package eu.inn.hyperbus.impl

import eu.inn.hyperbus.protocol.annotations.impl.{ContentTypeMarker, UrlMarker}
import eu.inn.hyperbus.protocol.annotations.method
import eu.inn.hyperbus.protocol.{Post, Request, Body, Response}
import eu.inn.hyperbus.serialization.RequestDecoder

import scala.concurrent.Future
import scala.reflect.macros.blackbox.Context

private[hyperbus] object HyperBusMacro {

  def subscribe[IN: c.WeakTypeTag]
    (c: Context)
    (groupName: c.Expr[Option[String]])
    (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {

    import c.universe._

    val thiz = c.prefix.tree

    val in = weakTypeOf[IN]
    val url = getUrlAnnotation(c)(in) getOrElse {
      c.abort(c.enclosingPosition, s"@url annotation is not defined for $in.}")
    }

    val requestTypeSig = c.typeOf[Request[_]].typeSymbol.typeSignature

    val (method: String, bodySymbol) = in.baseClasses.flatMap { baseSymbol =>
      val baseType = in.baseType(baseSymbol)
      baseType.baseClasses.find(_.typeSignature =:= requestTypeSig).flatMap { requestTrait =>
        getMethodAnnotation(c)(baseSymbol.typeSignature) map { annotationOfMethod =>
          (annotationOfMethod, in.baseType(requestTrait).typeArgs.head)
        }
      }
    }.headOption.getOrElse {
      c.abort(c.enclosingPosition, s"@method annotation is not defined.}")
    }

    val contentType: Option[String] = getContentTypeAnnotation(c)(bodySymbol)

    val obj = q"""{
      import eu.inn.servicebus.serialization._
      import eu.inn.hyperbus.serialization._
      import eu.inn.hyperbus.protocol._
      val thiz = $thiz
      val requestDecoder = eu.inn.hyperbus.serialization.HyperJsonDecoder.createRequestDecoder[$in]
      val responseEncoder = new Encoder[Response[Body]] {
        def encode(response: Response[Body], outputStream: java.io.OutputStream) = {
          val encoder: Encoder[Response[Body]] =
            response match {
              case b1: Created[TestCreatedBody] => HyperJsonEncoder.createEncoder[Created[TestCreatedBody]]
              case _ => throw new RuntimeException("todo: implement fallback")
            }
          encoder.encode(response, outputStream)
        }
      }

      val handler = (response: $in) => {
        eu.inn.servicebus.transport.SubscriptionHandlerResult[Response[Body]]($handler(response),responseEncoder)
      }
      val id = thiz.subscribe($url, $method, $contentType, $groupName, requestDecoder)(handler)
      id
    }"""
    //<-- response encoders
    println(obj)
    c.Expr[String](obj)
  }

  def send[OUT: c.WeakTypeTag, IN: c.WeakTypeTag]
  (c: Context)
  (r: c.Expr[IN]): c.Tree = {
    import c.universe._

    val in = weakTypeOf[IN]
    val out = weakTypeOf[OUT]
    val thiz = c.prefix.tree
    val obj = q"""{
      val thiz = $thiz
      thiz.send($r, null, null)
    }"""
    //<-- response decoders
    println(obj) // <--
    obj
  }

  private def getUrlAnnotation(c: Context)(t: c.Type): Option[String] =
    getStringAnnotation(c)(t.typeSymbol, c.typeOf[UrlMarker])

  private def getContentTypeAnnotation(c: Context)(t: c.Type): Option[String] =
    getStringAnnotation(c)(t.typeSymbol, c.typeOf[ContentTypeMarker])

  private def getMethodAnnotation(c: Context)(t: c.Type): Option[String] =
    getStringAnnotation(c)(t.typeSymbol, c.typeOf[method])

  def getStringAnnotation(c: Context)(symbol: c.Symbol, atype: c.Type): Option[String] = {
    import c.universe._
    symbol.annotations.find { a =>
      a.tree.tpe <:< atype
    } map {
      annotation => annotation.tree.children.tail.head match {
        case Literal(Constant(s: String)) => Some(s)
        case _ => None
      }
    } flatten
  }
}
