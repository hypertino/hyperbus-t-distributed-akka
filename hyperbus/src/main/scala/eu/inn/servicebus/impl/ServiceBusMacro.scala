package eu.inn.servicebus.impl

import eu.inn.servicebus.transport.SubscriptionHandlerResult

import scala.concurrent.Future
import scala.reflect.macros.blackbox.Context

private[servicebus] object ServiceBusMacro {
  def send[OUT: c.WeakTypeTag, IN: c.WeakTypeTag](c: Context)(topic: c.Expr[String],
                                                              message: c.Expr[IN]): c.Expr[Future[OUT]] = {
    import c.universe._

    val thiz = c.prefix.tree

    val out = weakTypeOf[OUT]
    val in = weakTypeOf[IN]

    val obj = q"""{
      val encoder = eu.inn.servicebus.serialization.createEncoder[$in]
      val decoder = eu.inn.servicebus.serialization.createDecoder[$out]
      val thiz = $thiz
      thiz.send[$out,$in]($topic,$message,encoder,decoder)
    }"""
    //println(obj)
    c.Expr[Future[OUT]](obj)
  }

  def subscribe[OUT: c.WeakTypeTag, IN: c.WeakTypeTag]
    (c: Context)
    (topic: c.Expr[String], groupName: c.Expr[Option[String]])
    (handler: c.Expr[(IN) => Future[OUT]]): c.Expr[String] = {

    import c.universe._

    val thiz = c.prefix.tree

    val out = weakTypeOf[OUT]
    val in = weakTypeOf[IN]

    val obj = q"""{
      val encoder = eu.inn.servicebus.serialization.createEncoder[$in]
      val decoder = eu.inn.servicebus.serialization.createDecoder[$out]
      val thiz = $thiz
      val handler = $handler
      val id = thiz.subscribe[$out,$in]($topic,$groupName,decoder){
        (in:$in) => eu.inn.servicebus.transport.SubscriptionHandlerResult(handler(in), encoder)
      }
      id
    }"""
    //println(obj)
    c.Expr[String](obj)
  }
}
