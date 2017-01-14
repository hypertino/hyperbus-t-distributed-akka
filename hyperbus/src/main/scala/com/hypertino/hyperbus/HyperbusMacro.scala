package com.hypertino.hyperbus

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.model.annotations.{contentType, method, uri}
import com.hypertino.hyperbus.transport.api.Subscription
import rx.lang.scala.{Observable, Observer}

import scala.concurrent.Future
import scala.reflect.macros._
import scala.util.matching.Regex

private[hyperbus] object HyperbusMacro {

  def onCommand[IN <: Request[Body] : c.WeakTypeTag]
  (c: whitebox.Context)
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[Future[Subscription]] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperbusMacroImplementation
    bundle.onCommand[IN](handler)
  }

  def onEvent[IN: c.WeakTypeTag]
  (c: whitebox.Context)
  (observer: c.Expr[Observer[IN]]): c.Expr[Future[Subscription]] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperbusMacroImplementation
    bundle.onEvent[IN](None, observer)
  }

  def onEventForGroup[IN: c.WeakTypeTag]
  (c: whitebox.Context)
  (groupName: c.Expr[String], observer: c.Expr[Observer[IN]]): c.Expr[Future[Subscription]] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperbusMacroImplementation
    bundle.onEvent[IN](Some(groupName), observer)
  }

  def ask[IN <: Request[Body] : c.WeakTypeTag]
  (c: whitebox.Context)
  (request: c.Expr[IN]): c.Expr[Any] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperbusMacroImplementation
    bundle.ask[IN](request)
  }

  def publish[IN <: Request[Body] : c.WeakTypeTag]
  (c: whitebox.Context)
  (request: c.Expr[IN]): c.Tree = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperbusMacroImplementation
    bundle.publish[IN](request)
  }
}

private[hyperbus] trait HyperbusMacroImplementation {
  val c: whitebox.Context

  import c.universe._

  def onCommand[IN <: Request[Body] : c.WeakTypeTag]
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[Future[Subscription]] = {

    val thiz = c.prefix.tree
    val requestType = weakTypeOf[IN]
    if (requestType.companion == null) {
      c.abort(c.enclosingPosition, s"Can't find companion object for $requestType (required to deserialize)")
    }
    val requestDeserializer = requestType.companion.decl(TermName("apply"))
    val methodGetter = requestType.companion.decl(TermName("method"))
    val uriPattern = getUriAnnotation(requestType)
    val bodySymbol = getBodySymbol(requestType)
    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val thizVal = fresh("thiz")
    val rmVal = fresh("requestMatcher")
    val requestVal = fresh("request")

    val obj =
      q"""{
      val $thizVal = $thiz
      val $rmVal = $thizVal.macroApiImpl.requestMatcher($uriPattern, $methodGetter, $contentType)
      $thizVal.onCommand[com.hypertino.hyperbus.model.Response[com.hypertino.hyperbus.model.Body],$requestType]($rmVal, $requestDeserializer _) {
        $requestVal: $requestType => $handler($requestVal)
      }
    }"""
    //println(obj)
    c.Expr[Future[Subscription]](obj)
  }

  def onEvent[IN: c.WeakTypeTag]
  (groupName: Option[c.Expr[String]], observer: c.Expr[Observer[IN]]): c.Expr[Future[Subscription]] = {
    val thiz = c.prefix.tree
    val requestType = weakTypeOf[IN]
    if (requestType.companion == null) {
      c.abort(c.enclosingPosition, s"Can't find companion object for $requestType (required to deserialize)")
    }
    val requestDeserializer = requestType.companion.decl(TermName("apply"))
    val methodGetter = requestType.companion.decl(TermName("method"))
    val uriPattern = getUriAnnotation(requestType)
    val bodySymbol = getBodySymbol(requestType)
    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)

    val thizVal = fresh("thiz")
    val rmVal = fresh("requestMatcher")
    val responseVal = fresh("response")

    val obj =
      q"""{
      val $thizVal = $thiz
      val $rmVal = $thizVal.macroApiImpl.requestMatcher($uriPattern, $methodGetter, $contentType)
      $thizVal.onEvent[$requestType]($rmVal, $groupName, $requestDeserializer _, $observer)
    }"""
//    println(obj)
    c.Expr[Future[Subscription]](obj)
  }

  def ask[IN <: Request[Body] : c.WeakTypeTag](r: c.Expr[IN]): c.Expr[Any] = {
    val in = weakTypeOf[IN]
    val thiz = c.prefix.tree

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
      val deserializer = body.companion.decl(TermName("apply"))
      //if (ta.isEmpty)
      //  c.abort(c.enclosingPosition, s"@contentType is not defined for $body")
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

    val obj =
      q"""{
      val $thizVal = $thiz
      val $responseDeserializerVal = $thizVal.macroApiImpl.responseDeserializer(
        _: com.hypertino.hyperbus.serialization.ResponseHeader,
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

    val obj =
      q"""{
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
      val tOr = typeOf[com.hypertino.hyperbus.model.|[_, _]].typeSymbol.typeSignature
      val tAsk = typeOf[com.hypertino.hyperbus.model.!].typeSymbol.typeSignature

      tin.flatMap { t =>
        if (t.typeSymbol.typeSignature <:< tOr) {
          getResponsesIn(t.typeArgs)
        } else if (t.typeSymbol.typeSignature <:< tAsk) {
          Seq.empty
        } else {
          Seq(t)
        }
      }
    }
  }

  private def getUriAnnotation(t: c.Type): String =
    getStringAnnotation(t, c.typeOf[uri]).getOrElse {
      c.abort(c.enclosingPosition, s"@uri annotation is not defined for $t.}")
    }

  private def getContentTypeAnnotation(t: c.Type): Option[String] = {
    getStringAnnotation(t, c.typeOf[contentType])
  }

  private def getMethodAnnotation(t: c.Type): Option[String] =
    getStringAnnotation(t, c.typeOf[method])

  private def getStringAnnotation(t: c.Type, atype: c.Type): Option[String] = {
    val typeChecked = c.typecheck(q"(??? : ${t.typeSymbol})").tpe
    val symbol = typeChecked.typeSymbol

    symbol.annotations.find { a =>
      a.tree.tpe <:< atype
    } flatMap {
      annotation => annotation.tree.children.tail.head match {
        case Literal(Constant(s: String)) => Some(s)
        case _ => None
      }
    }
  }

  private def fresh(prefix: String): TermName = TermName(c.freshName(prefix))
}