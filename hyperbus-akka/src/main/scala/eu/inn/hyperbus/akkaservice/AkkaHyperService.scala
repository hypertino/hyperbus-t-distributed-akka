package eu.inn.hyperbus.akkaservice

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.akkaservice.annotations.group
import eu.inn.hyperbus.model.{Body, Response}
import eu.inn.hyperbus.transport.api.Subscription

import scala.concurrent.Future
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

object AkkaHyperService {
  def route[A](hyperbus: Hyperbus, actorRef: ActorRef): Future[List[Subscription]] = macro AkkaHyperServiceMacro.route[A]

  def dispatch[A](actor: A): Receive = macro AkkaHyperServiceMacro.dispatch[A]
}

private[akkaservice] object AkkaHyperServiceMacro {

  def route[A: c.WeakTypeTag]
  (c: Context)
  (hyperbus: c.Expr[Hyperbus], actorRef: c.Expr[ActorRef]): c.Expr[Future[List[Subscription]]] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with AkkaHyperServiceImplementation
    val r = bundle.route[A](hyperbus.tree, actorRef.tree)
    c.Expr[Future[List[Subscription]]](r)
  }

  def routeTo[A: c.WeakTypeTag]
  (c: Context)
  (actorRef: c.Expr[ActorRef]): c.Expr[Future[List[Subscription]]] = {
    import c.universe._
    val tVar = c.freshName(TermName("t"))
    val r =
      q"""{
      val $tVar = ${c.prefix.tree}
      AkkaHyperService.route[${weakTypeOf[A]}]($tVar.hyperbus, $actorRef)
    }"""
    c.Expr[Future[List[Subscription]]](r)
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

  def route[A: c.WeakTypeTag](hyperbus: c.Tree, actorRef: c.Tree): c.Tree = {
    val onMethods = extractOnMethods[A]
    if (onMethods.isEmpty) {
      c.abort(c.enclosingPosition, s"No suitable 'onCommand' / '~>' or 'OnEvent' / '|>' method is defined in ${weakTypeOf[A]}")
    }

    val hyperbusVal = fresh("hyperbus")
    val actorVal = fresh("actorVal")
    val typ = weakTypeOf[A]
    //val defaultGroup = groupName map { s ⇒ q"Some($s)" } getOrElse q"None"

    val subscriptions = onMethods map { m ⇒
      val arg = m.paramLists.head.head
      val argType = arg.typeSignatureIn(typ)
      val messageVal = fresh("message")

      getGroupAnnotation(m).map { groupName ⇒
        q"""
          $hyperbusVal.onEventForGroup[$argType]($groupName, new Observer[$argType]{
            override def onNext(message: $argType): Unit = {
              _root_.akka.pattern.ask($actorVal, message).mapTo[Unit]
            }
          })
        """
      } getOrElse {
        val resultType = m.returnType
        val responseBodyTypeSig = typeOf[Response[Body]]
        val innerResultType = if (resultType.typeArgs.head <:< responseBodyTypeSig) {
          resultType.typeArgs.head
        }
        else {
          responseBodyTypeSig
        }

        q"""
          $hyperbusVal.~>[$argType]{ case $messageVal =>
            _root_.akka.pattern.ask($actorVal, $messageVal).mapTo[$innerResultType]
          }
        """
      }
    }

    val obj =
      q"""{
      val $hyperbusVal = $hyperbus
      val $actorVal = $actorRef
      scala.concurrent.Future.sequence(List(
        ..$subscriptions
      ))
    }"""
    // println(obj)
    obj
  }


  def dispatch[A: c.WeakTypeTag](actor: c.Tree): c.Tree = {
    val onMethods = extractOnMethods[A]
    if (onMethods.isEmpty) {
      c.abort(c.enclosingPosition, s"No suitable 'onCommand' / '~>' or 'onEvent' / '|>' method is defined in ${weakTypeOf[A]}")
    }

    val typ = weakTypeOf[A]

    val cases = onMethods map { m ⇒
      val messageVal = fresh("message")
      val methodName = m.asMethod.name
      val arg = m.paramLists.head.head
      val argType = arg.typeSignatureIn(typ)
      val resultType = m.returnType
      //println(s"a: $argType r: $resultType")
//      val innerResultType = resultType.typeArgs.head

      cq"""
        $messageVal: $argType => $methodName($messageVal) pipeTo sender
      """
    }

    val obj =
      q"""{
      import _root_.akka.pattern.pipe
      _ match {
        case ..$cases
      }
    }"""
//     println(obj)
    obj
  }

  protected def extractOnMethods[A: c.WeakTypeTag]: List[MethodSymbol] = {
    val fts = weakTypeOf[Future[_]].typeSymbol.typeSignature
    weakTypeOf[A].members.filter(member => member.isMethod &&
      (member.name.decodedName.toString.startsWith("onCommand") ||
        member.name.decodedName.toString.startsWith("onEvent") ||
        member.name.decodedName.toString.startsWith("~>") || // ~>
        member.name.decodedName.toString.startsWith("|>")) && // |>
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

  private def fresh(prefix: String): TermName = TermName(c.freshName(prefix))
}