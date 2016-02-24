package eu.inn.hyperbus

import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{contentType, method, uri}

import scala.concurrent.Future
import scala.reflect.macros._
import scala.util.matching.Regex

private[hyperbus] object HyperBusMacro {

  def onCommand[IN <: Request[Body] : c.WeakTypeTag]
  (c: blackbox.Context)
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.onCommand[IN](handler)
  }

  def onEvent[IN : c.WeakTypeTag]
  (c: blackbox.Context)(handler: c.Expr[(IN) => Future[Unit]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.onEvent[IN](None, handler)
  }

  def onEventForGroup[IN : c.WeakTypeTag]
  (c: blackbox.Context)(groupName: c.Expr[String], handler: c.Expr[(IN) => Future[Unit]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.onEvent[IN](Some(groupName), handler)
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

  def onCommand[IN <: Request[Body] : c.WeakTypeTag]
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {

    val thiz = c.prefix.tree
    val requestType = weakTypeOf[IN]
    if (requestType.companion == null) {
      c.abort(c.enclosingPosition, s"Can't find companion object for $requestType (required to deserialize)")
    }
    val requestDeserializer = requestType.companion.declaration(TermName("apply"))
    val methodGetter = requestType.companion.declaration(TermName("method"))
    val uriPattern = getUriAnnotation(requestType)
    val bodySymbol = getBodySymbol(requestType)
    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val thizVal = fresh("thiz")
    val rmVal = fresh("requestMatcher")

    val obj = q"""{
      val $thizVal = $thiz
      val $rmVal = $thizVal.macroApiImpl.requestMatcher($uriPattern, $methodGetter, $contentType)
      $thizVal.onCommand[eu.inn.hyperbus.model.Response[eu.inn.hyperbus.model.Body],$requestType]($rmVal, $requestDeserializer _) {
        response: $requestType => $handler(response)
      }
    }"""
    //println(obj)
    c.Expr[String](obj)
  }

  def onEvent[IN : c.WeakTypeTag]
  (groupName: Option[c.Expr[String]], handler: c.Expr[(IN) => Future[Unit]]): c.Expr[String] = {
    val thiz = c.prefix.tree
    val requestType = weakTypeOf[IN]
    if (requestType.companion == null) {
      c.abort(c.enclosingPosition, s"Can't find companion object for $requestType (required to deserialize)")
    }
    val requestDeserializer = requestType.companion.declaration(TermName("apply"))
    val methodGetter = requestType.companion.declaration(TermName("method"))
    val uriPattern = getUriAnnotation(requestType)
    val bodySymbol = getBodySymbol(requestType)
    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val thizVal = fresh("thiz")
    val rmVal = fresh("requestMatcher")
    val responseVal = fresh("response")

    val obj = q"""{
      val $thizVal = $thiz
      val $rmVal = $thizVal.macroApiImpl.requestMatcher($uriPattern, $methodGetter, $contentType)
      $thizVal.onEvent[$requestType]($rmVal, $groupName, $requestDeserializer _) {
        case $responseVal: $requestType => $handler($responseVal)
      }
    }"""
    //println(obj)
    c.Expr[String](obj)
  }

  def ask[IN <: Request[Body] : c.WeakTypeTag](r: c.Expr[IN]): c.Expr[Any] = {
    val in = weakTypeOf[IN]
    val thiz = c.prefix.tree

    //val url = getUriAnnotation(in)
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
      val deserializer = body.companion.declaration(TermName("apply"))
      if (ta.isEmpty)
        c.abort(c.enclosingPosition, s"@contentType is not defined for $body")
      cq"""$ta => $deserializer _"""
    }

    val thizVal = fresh("thiz")
    val responseDeserializerVal = fresh("responseDeserializer")

    val responses = getResponses(in)
    val send =
      if (responses.size == 1)
        q"$thizVal.ask($r, $responseDeserializerVal).asInstanceOf[scala.concurrent.Future[${responses.head}]]"
      else
        q"$thizVal.ask($r, $responseDeserializerVal)"

    val obj = q"""{
      val $thizVal = $thiz
      val $responseDeserializerVal = $thizVal.macroApiImpl.responseDeserializer(
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
    val thiz = c.prefix.tree
    val thizVal = fresh("thiz")

    val obj = q"""{
      val $thizVal = $thiz
      $thizVal.publish($r)
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

  private def getBodySymbol(in: Type) = {
    val requestTypeSig = typeOf[Request[_]].typeSymbol.typeSignature
    in.baseClasses.flatMap { baseSymbol =>
      val baseType = in.baseType(baseSymbol)
      baseType.baseClasses.find(_.typeSignature =:= requestTypeSig).flatMap { requestTrait =>
        in.baseType(requestTrait).typeArgs.headOption
      }
    }.headOption.getOrElse {
      c.abort(c.enclosingPosition, s"Body symbol of Request[Body] is not defined.}")
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

  private def getUriAnnotation(t: c.Type): String =
    getStringAnnotation(t.typeSymbol, c.typeOf[uri]).getOrElse {
      c.abort(c.enclosingPosition, s"@uri annotation is not defined for $t.}")
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

  private def fresh(prefix: String): TermName = newTermName(c.fresh(prefix))
}