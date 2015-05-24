package eu.inn.servicebus

import eu.inn.servicebus.transport.{PublishResult}

import scala.concurrent.Future
import scala.reflect.macros.blackbox.Context

private[servicebus] object ServiceBusMacro {
  def ask[OUT: c.WeakTypeTag, IN: c.WeakTypeTag](c: Context)(topic: c.Expr[String],
                                                              message: c.Expr[IN]): c.Expr[Future[OUT]] = {
    import c.universe._

    val thiz = c.prefix.tree

    val out = weakTypeOf[OUT]
    val in = weakTypeOf[IN]

    val obj = q"""{
      val encoder = eu.inn.servicebus.serialization.createEncoder[$in]
      val decoder = eu.inn.servicebus.serialization.createDecoder[$out]
      val thiz = $thiz
      thiz.ask[$out,$in]($topic,$message,encoder,decoder)
    }"""
    //println(obj)
    c.Expr[Future[OUT]](obj)
  }

  def publish[IN: c.WeakTypeTag](c: Context)
                                (topic: c.Expr[String],message: c.Expr[IN]): c.Expr[Future[PublishResult]] = {
    import c.universe._

    val thiz = c.prefix.tree

    val in = weakTypeOf[IN]

    val obj = q"""{
      val encoder = eu.inn.servicebus.serialization.createEncoder[$in]
      val thiz = $thiz
      thiz.publish[$in]($topic,$message,encoder)
    }"""
    //println(obj)
    c.Expr[Future[PublishResult]](obj)
  }

  def on[OUT: c.WeakTypeTag, IN: c.WeakTypeTag]
    (c: Context)
    (topic: c.Expr[String])
    (handler: c.Expr[(IN) => Future[OUT]]): c.Expr[String] = {

    import c.universe._

    val thiz = c.prefix.tree

    val out = weakTypeOf[OUT]
    val in = weakTypeOf[IN]

    val obj = q"""{
      val decoder = eu.inn.servicebus.serialization.createDecoder[$in]
      val encoder = eu.inn.servicebus.serialization.createEncoder[$out]
      val thiz = $thiz
      val handler = $handler
      val id = thiz.on[$out,$in]($topic,decoder){
        (in:$in) => eu.inn.servicebus.transport.SubscriptionHandlerResult(handler(in), encoder)
      }
      id
    }"""
    //println(obj)
    c.Expr[String](obj)
  }

  def subscribe[IN: c.WeakTypeTag]
  (c: Context)
  (topic: c.Expr[String], groupName: c.Expr[String])
  (handler: c.Expr[(IN) => Future[Unit]]): c.Expr[String] = {

    import c.universe._

    val thiz = c.prefix.tree

    val in = weakTypeOf[IN]

    val obj = q"""{
      val decoder = eu.inn.servicebus.serialization.createDecoder[$in]
      val thiz = $thiz
      val handler = $handler
      val id = thiz.subscribe[$in]($topic,$groupName,decoder){
        (in:$in) => eu.inn.servicebus.transport.SubscriptionHandlerResult(handler(in), null)
      }
      id
    }"""
    //println(obj)
    c.Expr[String](obj)
  }
}
