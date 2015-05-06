package eu.inn.hyperbus

import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.protocol.annotations.impl.{ContentTypeMarker, UrlMarker}
import eu.inn.hyperbus.protocol.annotations.method

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

    val dynamicBodyTypeSig = typeOf[DynamicBody].typeSymbol.typeSignature
    val bodyCases: Seq[c.Tree] = getUniqueResponseBodies(in).filterNot{
      _.typeSymbol.typeSignature =:= dynamicBodyTypeSig
    } map { body =>
      cq"_: $body => hbs.createEncoder[Response[$body]].asInstanceOf[hbs.ResponseEncoder]"
    }

    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val obj = q"""{
      import eu.inn.hyperbus.protocol._
      import eu.inn.hyperbus.{serialization=>hbs}
      import eu.inn.{servicebus=>sb}
      val thiz = $thiz
      val requestDecoder = hbs.createRequestDecoder[$in]
      val responseEncoder = thiz.responseEncoder(
        _: Response[Body],
        _: java.io.OutputStream,
        _.body match {
          case ..$bodyCases
        }
      )
      thiz.on($url, $method, $contentType, $groupName, requestDecoder) { case (response: $in) =>
        sb.transport.SubscriptionHandlerResult[Response[Body]]($handler(response),responseEncoder)
      }
    }"""
    //println(obj)
    c.Expr[String](obj)
  }

  def ask[IN: c.WeakTypeTag](r: c.Expr[IN]): c.Tree = {
    val in = weakTypeOf[IN]
    val thiz = c.prefix.tree

    val responseBodyTypes = getUniqueResponseBodies(in)

    responseBodyTypes.groupBy(getContentTypeAnnotation(_) getOrElse "") foreach { kv =>
      if (kv._2.size > 1) {
        c.abort(c.enclosingPosition, s"Ambiguous responses for contentType: '${kv._1}': ${kv._2.mkString(",")}")
      }
    }

    val bodyCases: Seq[c.Tree] = responseBodyTypes map { body =>
      val ta = getContentTypeAnnotation(body)
      cq"$ta => eu.inn.hyperbus.serialization.createResponseBodyDecoder[$body]"
    }

    val responses = getResponses(in)
    val send =
      if (responses.size == 1)
        q"thiz.ask($r, requestEncoder, responseDecoder).asInstanceOf[Future[${responses.head}]]"
      else
        q"thiz.ask($r, requestEncoder, responseDecoder)"

    val obj = q"""{
      import eu.inn.hyperbus.{serialization=>hsr}
      val thiz = $thiz
      val requestEncoder = hsr.createEncoder[$in].asInstanceOf[eu.inn.servicebus.serialization.Encoder[Request[Body]]]
      val responseDecoder = thiz.responseDecoder(
        _: hsr.ResponseHeader,
        _: com.fasterxml.jackson.core.JsonParser,
        _.contentType match {
          case ..$bodyCases
        }
      )
      $send
    }"""
    //println(obj)
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