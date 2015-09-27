package eu.inn.hyperbus

import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{contentType, method, url}

import scala.concurrent.Future
import scala.reflect.macros._
import scala.util.matching.Regex

private[hyperbus] object HyperBusMacro {

  def process[IN <: Request[Body] : c.WeakTypeTag]
  (c: blackbox.Context)
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.process[IN](handler)
  }

  def subscribe[IN <: Request[Body] : c.WeakTypeTag]
  (c: blackbox.Context)
  (groupName: c.Expr[String])
  (handler: c.Expr[(IN) => Future[Unit]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.subscribe[IN](groupName)(handler)
  }

  def ask[IN <: Request[Body] : c.WeakTypeTag]
  (c: whitebox.Context)
  (request: c.Expr[IN]): c.Expr[Any] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.ask[IN](request)
  }

  def publish[IN <: Request[Body] : c.WeakTypeTag]
  (c: blackbox.Context)
  (request: c.Expr[IN]): c.Tree = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.publish[IN](request)
  }
}

private[hyperbus] trait HyperBusMacroImplementation {
  val c: blackbox.Context

  import c.universe._

  def process[IN <: Request[Body] : c.WeakTypeTag]
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {

    val thiz = c.prefix.tree
    val requestType = weakTypeOf[IN]
    if (requestType.companion == null) {
      c.abort(c.enclosingPosition, s"Can't find companion object for $requestType (required to deserialize)")
    }
    val requestDeserializer = requestType.companion.declaration(TermName("deserializer"))
    val url = getUrlAnnotation(requestType)
    val (method: String, bodySymbol) = getMethodAndBody(requestType)
    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val obj = q"""{
      val thiz = $thiz
      val topic = thiz.macroApiImpl.topicWithAnyValue($url)
      thiz.process[Response[Body],$requestType](topic, $method, $contentType, $requestDeserializer _) {
        response: $requestType => $handler(response)
      }
    }"""
    //println(obj)
    c.Expr[String](obj)
  }

  def subscribe[IN <: Request[Body] : c.WeakTypeTag]
  (groupName: c.Expr[String])
  (handler: c.Expr[(IN) => Future[Unit]]): c.Expr[String] = {

    val thiz = c.prefix.tree
    val requestType = weakTypeOf[IN]
    if (requestType.companion == null) {
      c.abort(c.enclosingPosition, s"Can't find companion object for $requestType (required to deserialize)")
    }
    val requestDeserializer = requestType.companion.declaration(TermName("deserializer"))
    val url = getUrlAnnotation(requestType)
    val (method: String, bodySymbol) = getMethodAndBody(requestType)
    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val obj = q"""{
      val thiz = $thiz
      val topic = thiz.macroApiImpl.topicWithAnyValue($url)
      thiz.subscribe[$requestType](topic, $method, $contentType, $groupName, $requestDeserializer _) {
        response: $requestType => $handler(response)
      }
    }"""
    //println(obj)
    c.Expr[String](obj)
  }

  def ask[IN <: Request[Body] : c.WeakTypeTag](r: c.Expr[IN]): c.Expr[Any] = {
    val in = weakTypeOf[IN]
    val thiz = c.prefix.tree

    //val url = getUrlAnnotation(in)
    val responseBodyTypes = getUniqueResponseBodies(in)

    responseBodyTypes.groupBy(getContentTypeAnnotation(_) getOrElse "") foreach { kv =>
      if (kv._2.size > 1) {
        c.abort(c.enclosingPosition, s"Ambiguous responses for contentType: '${kv._1}': ${kv._2.mkString(",")}")
      }
    }

    val dynamicBodyTypeSig = typeOf[DynamicBody].typeSymbol.typeSignature
    val bodyCases: Seq[c.Tree] = responseBodyTypes.filterNot { t ⇒
      t.typeSymbol.typeSignature =:= dynamicBodyTypeSig
    } map { body =>
      val ta = getContentTypeAnnotation(body)
      val deserializer = body.companion.declaration(TermName("deserializer"))
      if (ta.isEmpty)
        c.abort(c.enclosingPosition, s"@contentType is not defined for $body")
      cq"""$ta => $deserializer _"""
    }

    val responses = getResponses(in)
    val send =
      if (responses.size == 1)
        q"thiz.ask($r, responseDeserializer).asInstanceOf[Future[${responses.head}]]"
      else
        q"thiz.ask($r, responseDeserializer)"

    val obj = q"""{
      val thiz = $thiz
      val responseDeserializer = thiz.macroApiImpl.responseDeserializer(
        _: eu.inn.hyperbus.serialization.ResponseHeader,
        _: com.fasterxml.jackson.core.JsonParser,
        _.contentType match {
          case ..$bodyCases
        }
      )
      $send
    }"""
    //println(obj)
    c.Expr(obj)
  }

  def publish[IN <: Request[Body] : c.WeakTypeTag](r: c.Expr[IN]): c.Tree = {
    val in = weakTypeOf[IN]
    val url = getUrlAnnotation(in)
    val thiz = c.prefix.tree

    val obj = q"""{
      val thiz = $thiz
      thiz.publish($r)
    }"""
    //println(obj)
    obj
  }

  private def getUniqueResponseBodies(t: c.Type): Seq[c.Type] = {
    getResponses(t).foldLeft(Seq[c.Type]())((seq, el) => {
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
      Seq.empty
    }
  }

  private def getResponsesIn(tin: Seq[c.Type]): Seq[c.Type] = {
    val tupleRegex = new Regex("^Tuple(\\d+)$")

    // DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]
    if (tin.length == 1 && tupleRegex.findFirstIn(tin.head.typeSymbol.name.toString).isDefined
    ) {
      tin.head.typeArgs
    } //DefinedResponse[|[Ok[DynamicBody], |[Created[TestCreatedBody], !]]]
    else {
      val tOr = typeOf[eu.inn.hyperbus.model.|[_, _]].typeSymbol.typeSignature
      val tAsk = typeOf[eu.inn.hyperbus.model.!].typeSymbol.typeSignature

      tin.flatMap { t =>
        if (t.typeSymbol.typeSignature <:< tOr) {
          getResponsesIn(t.typeArgs)
        } else
        if (t.typeSymbol.typeSignature <:< tAsk) {
          Seq.empty
        } else {
          Seq(t)
        }
      }
    }
  }

  private def getUrlAnnotation(t: c.Type): String =
    getStringAnnotation(t.typeSymbol, c.typeOf[url]).getOrElse {
      c.abort(c.enclosingPosition, s"@url annotation is not defined for $t.}")
    }

  private def getContentTypeAnnotation(t: c.Type): Option[String] =
    getStringAnnotation(t.typeSymbol, c.typeOf[contentType])

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