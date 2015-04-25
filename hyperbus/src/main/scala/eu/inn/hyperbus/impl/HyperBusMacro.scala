package eu.inn.hyperbus.impl

import eu.inn.hyperbus.protocol.annotations.impl.{ContentTypeMarker, UrlMarker}
import eu.inn.hyperbus.protocol.annotations.method
import eu.inn.hyperbus.protocol._

import scala.concurrent.Future
import scala.reflect.macros.blackbox.Context

private[hyperbus] object HyperBusMacro {

  def subscribe[IN: c.WeakTypeTag]
  (c: Context)
  (groupName: c.Expr[Option[String]])
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.subscribe[IN](groupName)(handler)
  }

  def send[OUT: c.WeakTypeTag, IN: c.WeakTypeTag]
  (c: Context)
  (r: c.Expr[IN]): c.Tree = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.send[OUT,IN](r)
  }
}

private[hyperbus] trait HyperBusMacroImplementation {
  val c: Context
  import c.universe._

  def subscribe[IN: c.WeakTypeTag]
  (groupName: c.Expr[Option[String]])
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {

    val thiz = c.prefix.tree

    val in = weakTypeOf[IN]
    val url = getUrlAnnotation(in) getOrElse {
      c.abort(c.enclosingPosition, s"@url annotation is not defined for $in.}")
    }

    val requestTypeSig = typeOf[Request[_]].typeSymbol.typeSignature

    val (method: String, bodySymbol) = in.baseClasses.flatMap { baseSymbol =>
      val baseType = in.baseType(baseSymbol)
      baseType.baseClasses.find(_.typeSignature =:= requestTypeSig).flatMap { requestTrait =>
        getMethodAnnotation(baseSymbol.typeSignature) map { annotationOfMethod =>
          (annotationOfMethod, in.baseType(requestTrait).typeArgs.head)
        }
      }
    }.headOption.getOrElse {
      c.abort(c.enclosingPosition, s"@method annotation is not defined.}")
    }

    val definedResponses = getResponses(in)
    val uniqueBodyResponses = definedResponses.foldLeft(Set[c.Type]())((set,el) => {
      if (!set.exists(_ =:= el.typeArgs.head)) {
        set + el.typeArgs.head
      }
      else
        set
    })
    val bodyCases: Seq[c.Tree] = uniqueBodyResponses.toSeq.map { body =>
      cq"_: $body => eu.inn.hyperbus.serialization.createEncoder[Response[$body]]"
    }
    //println(uniqueBodyResponses)

    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val obj = q"""{
      import eu.inn.servicebus.serialization._
      import eu.inn.hyperbus.serialization._
      import eu.inn.hyperbus.protocol._
      val thiz = $thiz
      val requestDecoder = createRequestDecoder[$in]
      val responseEncoder: Encoder[Response[Body]] = (response: Response[Body], outputStream: java.io.OutputStream) => {
        response.body match {
          case ..$bodyCases
          case _ => throw new RuntimeException("todo: common handling need to be fixed")
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
  (r: c.Expr[IN]): c.Tree = {
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

  private def getResponses(t: c.Type): Seq[c.Type] = {
    val tDefined = typeOf[DefinedResponse[_]].typeSymbol.typeSignature

    t.baseClasses.find(_.typeSignature <:< tDefined) map { responses =>
      getResponsesIn(t.baseType(responses).typeArgs)
    } getOrElse {
      Seq()
    }
  }

  private def getResponsesIn(tin: Seq[c.Type]): Seq[c.Type] = {
    val tOr = typeOf[eu.inn.hyperbus.protocol.|[_,_]].typeSymbol.typeSignature
    val tAsk = typeOf[eu.inn.hyperbus.protocol.!].typeSymbol.typeSignature

    tin.flatMap { t =>
      if (t.typeSymbol.typeSignature <:< tOr) {
        getResponsesIn(t.typeArgs)
      } else
      if (t.typeSymbol.typeSignature <:< tAsk) {
        Seq()
      } else {
        Seq(t)
      }
    }
  }

  private def getUrlAnnotation(t: c.Type): Option[String] =
    getStringAnnotation(t.typeSymbol, c.typeOf[UrlMarker])

  private def getContentTypeAnnotation(t: c.Type): Option[String] =
    getStringAnnotation(t.typeSymbol, c.typeOf[ContentTypeMarker])

  private def getMethodAnnotation(t: c.Type): Option[String] =
    getStringAnnotation(t.typeSymbol, c.typeOf[method])

  private def getStringAnnotation(symbol: c.Symbol, atype: c.Type): Option[String] = {
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