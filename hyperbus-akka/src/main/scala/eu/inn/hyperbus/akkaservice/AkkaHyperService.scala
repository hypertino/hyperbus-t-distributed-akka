package eu.inn.hyperbus.akkaservice

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.akkaservice.annotations.group

import scala.concurrent.Future
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context


object AkkaHyperService {
  def route[A](hyperBus: HyperBus, actorRef: ActorRef): List[String] = macro AkkaHyperServiceMacro.route[A]

  def dispatch[A](actor: A): Receive = macro AkkaHyperServiceMacro.dispatch[A]
}

private[akkaservice] object AkkaHyperServiceMacro {

  def route[A: c.WeakTypeTag]
  (c: Context)
  (hyperBus: c.Expr[HyperBus], actorRef: c.Expr[ActorRef]): c.Expr[List[String]] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with AkkaHyperServiceImplementation
    val r = bundle.route[A](hyperBus.tree, actorRef.tree)
    c.Expr[List[String]](r)
  }

  def routeTo[A: c.WeakTypeTag]
  (c: Context)
  (actorRef: c.Expr[ActorRef]): c.Expr[List[String]] = {
    import c.universe._
    val r = q"""{
      val t = ${c.prefix.tree}
      AkkaHyperService.route[${weakTypeOf[A]}](t.hyperBus, $actorRef)
    }"""
    c.Expr[List[String]](r)
  }

  def dispatch[A: c.WeakTypeTag]
  (c: Context)
  (actor: c.Expr[A]): c.Expr[Receive] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with AkkaHyperServiceImplementation
    val r = bundle.dispatch[A](actor.tree)
    c.Expr[Receive](r)
  }
}

private[akkaservice] trait AkkaHyperServiceImplementation {
  val c: Context

  import c.universe._

  def route[A: c.WeakTypeTag](hyperBus: c.Tree, actorRef: c.Tree): c.Tree = {
    val onMethods = extractOnMethods[A]
    if (onMethods.isEmpty) {
      c.abort(c.enclosingPosition, s"No suitable 'on' or 'subscribe' method is defined in ${weakTypeOf[A]}")
    }

    val typ = weakTypeOf[A]
    //val defaultGroup = groupName map { s ⇒ q"Some($s)" } getOrElse q"None"

    val subscriptions = onMethods map { m ⇒
      val arg = m.paramLists.head.head
      val argType = arg.typeSignatureIn(typ)
      getGroupAnnotation(m).map { groupName ⇒
        q"""
          h.subscribe[$argType]($groupName){ message =>
            akka.pattern.ask(a, message).mapTo[Unit]
          }
        """
      } getOrElse {
        val resultType = m.returnType
        val innerResultType = resultType.typeArgs.head
        q"""
          h.on[$argType]{ message =>
            akka.pattern.ask(a, message).mapTo[$innerResultType]
          }
        """
      }
    }

    val obj = q"""{
      val h = $hyperBus
      val a = $actorRef
      List(
        ..$subscriptions
      )
    }"""
    // println(obj)
    obj
  }


  def dispatch[A: c.WeakTypeTag](actor: c.Tree): c.Tree = {
    val onMethods = extractOnMethods[A]
    if (onMethods.isEmpty) {
      c.abort(c.enclosingPosition, s"No suitable 'on' or 'subscribe' method is defined in ${weakTypeOf[A]}")
    }

    val typ = weakTypeOf[A]

    val cases = onMethods map { m ⇒
      val methodName = m.asMethod.name
      val arg = m.paramLists.head.head
      val argType = arg.typeSignatureIn(typ)
      val resultType = m.returnType
      //println(s"a: $argType r: $resultType")
      val innerResultType = resultType.typeArgs.head

      cq"""
        message: $argType => $methodName(message) pipeTo sender
      """
    }

    val obj = q"""{
      import akka.pattern.pipe
      _ match {
        case ..$cases
      }
    }"""
    // println(obj)
    obj
  }

  protected def extractOnMethods[A: c.WeakTypeTag]: List[MethodSymbol] = {
    val fts = weakTypeOf[Future[_]].typeSymbol.typeSignature
    weakTypeOf[A].members.filter(member => member.isMethod &&
      (member.name.decodedName.toString.startsWith("on") ||
        member.name.decodedName.toString.startsWith("subscribe")) &&
      member.isPublic && {
      val m = member.asInstanceOf[MethodSymbol]
      //println("method: " + member.name.decoded + " params: " + m.paramss)
      m.returnType.typeSymbol.typeSignature <:< fts &&
        ((m.paramLists.size == 1 && m.paramLists.head.size == 1) ||
          (m.paramLists.size == 2 && m.paramLists.head.size == 1 && allImplicits(m.paramLists.tail)))
    }
    ).map(_.asInstanceOf[MethodSymbol]).toList
  }

  private def allImplicits(symbols: List[List[Symbol]]): Boolean = !symbols.flatten.exists(!_.isImplicit)

  private def getGroupAnnotation(symbol: c.Symbol): Option[String] =
    getStringAnnotation(symbol, c.typeOf[group])

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