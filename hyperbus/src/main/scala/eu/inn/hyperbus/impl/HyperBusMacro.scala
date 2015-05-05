package eu.inn.hyperbus.impl

import eu.inn.hyperbus.protocol.annotations.impl.{ContentTypeMarker, UrlMarker}
import eu.inn.hyperbus.protocol.annotations.method
import eu.inn.hyperbus.protocol._

import scala.concurrent.Future
import scala.reflect.macros.blackbox.Context

private[hyperbus] object HyperBusMacro {

  def on[IN: c.WeakTypeTag]
  (c: Context)
  (groupName: c.Expr[Option[String]])
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.on[IN](groupName)(handler)
  }

  def ask[IN: c.WeakTypeTag]
  (c: Context)
  (r: c.Expr[IN]): c.Tree = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.ask[IN](r)
  }
}

private[hyperbus] trait HyperBusMacroImplementation {
  val c: Context
  import c.universe._

  def on[IN: c.WeakTypeTag]
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

    val dynamicBodyTypeSig = typeOf[Dynamic].typeSymbol.typeSignature
    val bodyCases: Seq[c.Tree] = getUniqueResponseBodies(in).filterNot{
      _.typeSymbol.typeSignature =:= dynamicBodyTypeSig
    } map { body =>
      cq"_: $body => eu.inn.hyperbus.serialization.createEncoder[Response[$body]](response.asInstanceOf[Response[$body]], outputStream)"
    }
    //println(uniqueBodyResponses)

    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val obj = q"""{
      import eu.inn.hyperbus.protocol._
      val thiz = $thiz
      val requestDecoder = eu.inn.hyperbus.serialization.createRequestDecoder[$in]
      val responseEncoder: eu.inn.servicebus.serialization.Encoder[Response[Body]] = (response: Response[Body], outputStream: java.io.OutputStream) => {
        response.body match {
          case ..$bodyCases
          case _: Dynamic => eu.inn.hyperbus.serialization.createEncoder[Response[Dynamic]](response.asInstanceOf[Response[Dynamic]], outputStream)
          case _ => throw new RuntimeException("todo: common handling need to be fixed")
        }
      }
      val handler = (response: $in) => {
        eu.inn.servicebus.transport.SubscriptionHandlerResult[Response[Body]]($handler(response),responseEncoder)
      }
      val id = thiz.on($url, $method, $contentType, $groupName, requestDecoder)(handler)
      id
    }"""
    //<-- response encoders
    //println(obj)
    c.Expr[String](obj)
  }

  def ask[IN: c.WeakTypeTag]
  (r: c.Expr[IN]): c.Tree = {
    val in = weakTypeOf[IN]
    val thiz = c.prefix.tree

    val responseBodyTypes = getUniqueResponseBodies(in)

    responseBodyTypes.groupBy(getContentTypeAnnotation(_) getOrElse "") foreach { kv =>
      if (kv._2.size > 1) {
        c.abort(c.enclosingPosition, s"Ambiguous responses for contentType: '${kv._1}': ${kv._2.mkString(",")}")
      }
    }

    val dynamicBodyTypeSig = typeOf[Dynamic].typeSymbol.typeSignature
    val normalCases: Seq[c.Tree] = responseBodyTypes.filterNot{
      _.typeSymbol.typeSignature =:= dynamicBodyTypeSig
    } map { body =>
      val ta = getContentTypeAnnotation(body)
      cq"$ta => eu.inn.hyperbus.serialization.createResponseBodyDecoder[$body]"
    }

    val dynamicCase = responseBodyTypes.find{
      _.typeSymbol.typeSignature =:= dynamicBodyTypeSig
    }.map { body =>
      cq"_ => eu.inn.hyperbus.serialization.createResponseBodyDecoder[$body]"
    }

    // todo: add fallback response handler

    val bodyCases = normalCases ++ dynamicCase

    val responses = getResponses(in)
    val send =
      if (responses.size == 1)
        q"thiz.ask($r, requestEncoder, responseDecoder).asInstanceOf[Future[${responses.head}]]"
      else
        q"thiz.ask($r, requestEncoder, responseDecoder)"

    val obj = q"""{
      val thiz = $thiz
      val requestEncoder = eu.inn.hyperbus.serialization.createEncoder[$in].asInstanceOf[eu.inn.servicebus.serialization.Encoder[Request[Body]]]
      val responseDecoder = eu.inn.hyperbus.serialization.createResponseDecoder {
        (responseHeader:  eu.inn.hyperbus.serialization.ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser) => {
          val decoder = responseHeader.contentType match {
            case ..$bodyCases
          }
          decoder(responseHeader, responseBodyJson)
        }
      }
      $send
    }"""
    //<-- response decoders
    //println(obj) // <--
    obj
  }

  private def getUniqueResponseBodies(t: c.Type): Seq[c.Type] = {
    getResponses(t).foldLeft(Seq[c.Type]())((seq,el) => {
      val bodyType = el.typeArgs.head
      if (!seq.exists(_ =:= bodyType)) {
        seq ++ Seq(el.typeArgs.head)
      }
      else
        seq
    })
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
    } flatMap {
      annotation => annotation.tree.children.tail.head match {
        case Literal(Constant(s: String)) => Some(s)
        case _ => None
      }
    }
  }
}