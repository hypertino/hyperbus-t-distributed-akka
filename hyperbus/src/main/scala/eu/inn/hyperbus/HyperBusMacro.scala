package eu.inn.hyperbus

import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{contentType, method, url}

import scala.concurrent.Future
import scala.reflect.macros.blackbox.Context

private[hyperbus] object HyperBusMacro {

  def process[IN <: Request[Body] : c.WeakTypeTag]
  (c: Context)
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.process[IN](handler)
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
  (request: c.Expr[IN]): c.Tree = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.ask[IN](request)
  }

  def publish[IN <: Request[Body] : c.WeakTypeTag]
  (c: Context)
  (request: c.Expr[IN]): c.Tree = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with HyperBusMacroImplementation
    bundle.publish[IN](request)
  }
}

private[hyperbus] trait HyperBusMacroImplementation {
  val c: Context

  import c.universe._

  def process[IN <: Request[Body] : c.WeakTypeTag]
  (handler: c.Expr[(IN) => Future[Response[Body]]]): c.Expr[String] = {

    val thiz = c.prefix.tree
    val requestType = weakTypeOf[IN]
    val requestCompanionName = requestType.companion.typeSymbol.name.toTermName // todo: generate error if no companion is defined
    val url = getUrlAnnotation(requestType)
    val (method: String, bodySymbol) = getMethodAndBody(requestType)
    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)
    /*val applyMethod = getDecoder(requestType.companion, typeOf[RequestDeserializer[_]]).getOrElse {
      c.abort(c.enclosingPosition, "Can't find method apply() compatible with RequestDeserializer")
    }*/

    val obj = q"""{
      val thiz = $thiz
      val topic = thiz.macroApiImpl.topicWithAnyValue($url)
      val requestDeserializer: eu.inn.hyperbus.serialization.RequestDeserializer[${requestType}] =
        $requestCompanionName.deserializer _
      thiz.process[Response[Body],$requestType](topic, $method, $contentType, requestDeserializer) { response: $requestType =>
        $handler(response)
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
    val requestCompanionName = requestType.companion.typeSymbol.name.toTermName // todo: generate error if no companion is defined
    val url = getUrlAnnotation(requestType)
    val (method: String, bodySymbol) = getMethodAndBody(requestType)
    val contentType: Option[String] = getContentTypeAnnotation(bodySymbol)
    /*val applyMethod = getDecoder(requestType.companion, typeOf[RequestDeserializer[_]]).getOrElse {
      c.abort(c.enclosingPosition, "Can't find method apply() compatible with RequestDeserializer")
    }*/

    val obj = q"""{
      val thiz = $thiz
      val topic = thiz.macroApiImpl.topicWithAnyValue($url)
      val requestDeserializer: eu.inn.hyperbus.serialization.RequestDeserializer[${requestType}] =
        $requestCompanionName.deserializer _
      thiz.subscribe[$requestType](topic, $method, $contentType, $groupName, requestDeserializer) { response: $requestType =>
        $handler(response)
      }
    }"""
    //println(obj)
    c.Expr[String](obj)
  }

  def ask[IN <: Request[Body] : c.WeakTypeTag](r: c.Expr[IN]): c.Tree = {
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
      val bodyCompanionName = body.companion.typeSymbol.name.toTermName
      if (ta.isEmpty)
        c.abort(c.enclosingPosition, s"@contentType is not defined for $body")
      /*val applyMethod = getDecoder(body.companion, typeOf[ResponseBodyDeserializer]).getOrElse {
        c.abort(c.enclosingPosition, "Can't find method apply() compatible with ResponseBodyDeserializer")
      }*/
      //val m = getApplyMethod(body.companion, typeOf[ResponseBodyDeserializer]).get
      cq"""$ta => {
        val rdb: eu.inn.hyperbus.serialization.ResponseBodyDeserializer = $bodyCompanionName.deserializer _
        rdb
      }"""
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
    obj
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

  /*private def getDecoder(companionType: c.Type, atype: c.Type): Option[MethodSymbol] = {
    companionType.declaration(newTermName("apply")) match {
      case NoSymbol => c.abort(c.enclosingPosition, s"No apply function found on $companionType")
      case s =>
        println(showRaw(s.asTerm.alternatives))
        // searches apply method corresponding to unapply
        val applies = s.asTerm.alternatives
        applies.collectFirst {
          case (apply: MethodSymbol)
            if matchApply(apply, atype) ⇒ apply
            //if (apply.paramss.headOption.map(_.map(_.asTerm.typeSignature)) == unapplyReturnTypes) => apply
        }
    }
  }

  def matchApply(apply: MethodSymbol, atype: Type): Boolean = {
    val rht = Request

    apply.paramss.headOption.map { params ⇒
      params.headOption
    }
  }*/
}