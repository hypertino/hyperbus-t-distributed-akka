package eu.inn.hyperbus

import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.annotations.impl.{ContentTypeMarker, UrlMarker}
import eu.inn.hyperbus.rest.annotations.method
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.PartitionArgs

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.macros.blackbox.Context

private[hyperbus] object HyperBusMacro {

  def on[IN <: Request[Body] : c.WeakTypeTag]
  (c: Context)
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.on[IN](handler)
  }

  def subscribe[IN <: Request[Body] : c.WeakTypeTag]
  (c: Context)
  (groupName: c.Expr[String])
  (handler: c.Expr[(IN) => Future[Unit]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.subscribe[IN](groupName)(handler)
  }

  def ask[IN <: Request[Body] : c.WeakTypeTag]
  (c: Context)
  (r: c.Expr[IN]): c.Tree = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.ask[IN](r)
  }

  def publish[IN <: Request[Body] : c.WeakTypeTag]
  (c: Context)
  (r: c.Expr[IN]): c.Tree = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.publish[IN](r)
  }
}

private[hyperbus] trait HyperBusMacroImplementation {
  val c: Context
  import c.universe._

  def on[IN <: Request[Body] : c.WeakTypeTag]
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {

    val thiz = c.prefix.tree

    val in = weakTypeOf[IN]
    val url = getUrlAnnotation(in)

    val (method: String, bodySymbol) = getMethodAndBody(in)

    val dynamicBodyTypeSig = typeOf[DynamicBody].typeSymbol.typeSignature
    val bodyCases: Seq[c.Tree] = getUniqueResponseBodies(in).filterNot{
      _.typeSymbol.typeSignature =:= dynamicBodyTypeSig
    } map { body =>
      cq"_: $body => hbs.createEncoder[Response[$body]].asInstanceOf[hbs.ResponseEncoder]"
    }

    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val obj = q"""{
      import eu.inn.hyperbus.rest._
      import eu.inn.hyperbus.{serialization=>hbs}
      import eu.inn.{servicebus=>sb}
      val thiz = $thiz
      val requestDecoder = hbs.createRequestDecoder[$in]
      val extractor = ${defineExtractor[IN](url)}
      val responseEncoder = thiz.responseEncoder(
        _: Response[Body],
        _: java.io.OutputStream,
        _.body match {
          case ..$bodyCases
        }
      )
      val topic = eu.inn.hyperbus.impl.Helpers.topicWithAllPartitions($url)
      thiz.on[Response[Body],$in](topic, $method, $contentType, requestDecoder, extractor) { case (response: $in) =>
        sb.transport.SubscriptionHandlerResult[Response[Body]]($handler(response),responseEncoder)
      }
    }"""
    //println(obj)
    c.Expr[String](obj)
  }

  def subscribe[IN <: Request[Body] : c.WeakTypeTag]
  (groupName: c.Expr[String])
  (handler: c.Expr[(IN) => Future[Unit]]): c.Expr[String] = {
    val thiz = c.prefix.tree

    val in = weakTypeOf[IN]
    val url = getUrlAnnotation(in)

    val (method: String, bodySymbol) = getMethodAndBody(in)
    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val obj = q"""{
      import eu.inn.hyperbus.rest._
      import eu.inn.hyperbus.{serialization=>hbs}
      import eu.inn.{servicebus=>sb}
      val thiz = $thiz
      val requestDecoder = hbs.createRequestDecoder[$in]
      val extractor = ${defineExtractor[IN](url)}
      val topic = eu.inn.hyperbus.impl.Helpers.topicWithAllPartitions($url)
      thiz.subscribe[$in](topic, $method, $contentType, $groupName, requestDecoder, extractor) { case (response: $in) =>
        sb.transport.SubscriptionHandlerResult[Unit]($handler(response),null)
      }
    }"""
    //println(obj)
    c.Expr[String](obj)
  }

  def ask[IN <: Request[Body] : c.WeakTypeTag](r: c.Expr[IN]): c.Tree = {
    val in = weakTypeOf[IN]
    val thiz = c.prefix.tree

    val url = getUrlAnnotation(in)
    val responseBodyTypes = getUniqueResponseBodies(in)

    responseBodyTypes.groupBy(getContentTypeAnnotation(_) getOrElse "") foreach { kv =>
      if (kv._2.size > 1) {
        c.abort(c.enclosingPosition, s"Ambiguous responses for contentType: '${kv._1}': ${kv._2.mkString(",")}")
      }
    }

    val dynamicBodyTypeSig = typeOf[DynamicBody].typeSymbol.typeSignature
    val bodyCases: Seq[c.Tree] = responseBodyTypes.filterNot{
      _.typeSymbol.typeSignature =:= dynamicBodyTypeSig
    } map { body =>
      val ta = getContentTypeAnnotation(body)
      if (ta.isEmpty)
        c.abort(c.enclosingPosition, s"@contentType is not defined for $body")
      cq"$ta => eu.inn.hyperbus.serialization.createResponseBodyDecoder[$body]"
    }

    val responses = getResponses(in)
    val send =
      if (responses.size == 1)
        q"thiz.ask($r, requestEncoder, extractor, responseDecoder).asInstanceOf[Future[${responses.head}]]"
      else
        q"thiz.ask($r, requestEncoder, extractor, responseDecoder)"

    val obj = q"""{
      import eu.inn.hyperbus.{serialization=>hbs}
      val thiz = $thiz
      val requestEncoder = hbs.createEncoder[$in]
      val extractor = ${defineExtractor[IN](url)}
      val responseDecoder = thiz.responseDecoder(
        _: hbs.ResponseHeader,
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

  def publish[IN <: Request[Body] : c.WeakTypeTag](r: c.Expr[IN]): c.Tree = {
    val in = weakTypeOf[IN]
    val url = getUrlAnnotation(in)
    val thiz = c.prefix.tree

    val obj = q"""{
      import eu.inn.hyperbus.{serialization=>hbs}
      val thiz = $thiz
      val requestEncoder = hbs.createEncoder[$in]
      val extractor = ${defineExtractor[IN](url)}
      thiz.publish($r, requestEncoder, extractor)
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

  private def getMethodAndBody(in: Type) = {
    val requestTypeSig = typeOf[Request[_]].typeSymbol.typeSignature
    in.baseClasses.flatMap { baseSymbol =>
      val baseType = in.baseType(baseSymbol)
      baseType.baseClasses.find(_.typeSignature =:= requestTypeSig).flatMap { requestTrait =>
        getMethodAnnotation(baseSymbol.typeSignature) map { annotationOfMethod =>
          (annotationOfMethod, in.baseType(requestTrait).typeArgs.head)
        }
      }
    }.headOption.getOrElse {
      c.abort(c.enclosingPosition, s"@method annotation is not defined.}")
    }
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
    val tOr = typeOf[eu.inn.hyperbus.rest.|[_,_]].typeSymbol.typeSignature
    val tAsk = typeOf[eu.inn.hyperbus.rest.!].typeSymbol.typeSignature

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

  private def getUrlAnnotation(t: c.Type): String =
    getStringAnnotation(t.typeSymbol, c.typeOf[UrlMarker]).getOrElse {
      c.abort(c.enclosingPosition, s"@url annotation is not defined for $t.}")
    }

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

  def defineExtractor[REQ <: Request[Body] : c.WeakTypeTag](url: String): c.Expr[PartitionArgsExtractor[REQ]] = {
    import c.universe._

    // todo: test urls with args
    val t = weakTypeOf[REQ]
    val lst = impl.Helpers.extractParametersFromUrl(url).map { arg ⇒
      q"$arg -> ExactValue(r.body.${TermName(arg)}.toString)" // todo remove toString if string
    }

    val obj = q"""{
      import eu.inn.servicebus.transport._
      (r:$t) => {
        PartitionArgs(Map(
          ..$lst
        ))
      }
    }"""

    c.Expr[PartitionArgsExtractor[REQ]](obj)
  }
}