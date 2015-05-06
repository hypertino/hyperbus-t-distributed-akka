package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.serialization.impl.Helpers
import eu.inn.hyperbus.serialization.{RequestDecoder, ResponseDecoder}
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.serialization.Encoder
import eu.inn.servicebus.transport.SubscriptionHandlerResult
import eu.inn.servicebus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.experimental.macros
import scala.util.{Random, Try}

/*
todo:
+ decide group result type
+ correlationId, sequenceId, replyTo
+ other headers?
+ exception when duplicate subscription
*/

class HyperBus(val underlyingBus: ServiceBus)(implicit val executionContext: ExecutionContext) {
  protected val subscriptions = new Subscriptions[Subscription]
  protected val randomGen = new Random()
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler)]

  protected case class Subscription(
                                     handler: (Request[Body]) => SubscriptionHandlerResult[Response[Body]],
                                     requestDecoder: RequestDecoder )


  protected class UnderlyingHandler(routeKey: String) {

    def unhandledRequest(request: Request[Body]): SubscriptionHandlerResult[Response[Body]] = {
      val s = "Unhandled request " + safe(()=>request.method) + routeKey +
        safe(()=>request.body.contentType.map("@"+_).getOrElse(""))
      log.error(s)

      val p = Promise[Response[Body]]()
      p.success(InternalError(ErrorBody(StandardErrors.HANDLER_NOT_FOUND, Some(s))))
      SubscriptionHandlerResult(p.future, null) //todo: test exception encoder
    }

    def handler(in: Request[Body]): SubscriptionHandlerResult[Response[Body]] = {
      getSubscription(in).map { s ⇒
        val r = s.handler(in)

        val f = r.futureResult.recover {
          case x: Response[_] ⇒ x
          case t: Throwable ⇒ InternalError(ErrorBody(StandardErrors.INTERNAL_ERROR)) // todo: internal error, log details on server
        }
        SubscriptionHandlerResult[Response[Body]](f, r.resultEncoder)
      } getOrElse {
        unhandledRequest(in)
      }
    }

    protected def getSubscription(in: Request[Body]): Option[Subscription] = getSubscription(in.method, in.body.contentType)

    protected def getSubscription(method:String, contentType: Option[String]): Option[Subscription] = {
      val subRouteKey = getSubRouteKey(method, contentType)

      subscriptions.get(routeKey).get(subRouteKey).orElse{
        subscriptions.get(routeKey).get(getSubRouteKey(method, None))
      } map { subscrSeq =>
        val idx = if (subscrSeq.size > 1) {
          randomGen.nextInt(subscrSeq.size)
        } else {
          0
        }
        subscrSeq(idx).subscription
      }
    }

    def decoder(inputStream: InputStream): Request[Body] = {
      // todo: handle deserialization errors
      Helpers.decodeRequestWith(inputStream) { (requestHeader, requestBodyJson) =>
        getSubscription(requestHeader.method, requestHeader.contentType) map { subscription =>
          subscription.requestDecoder(requestHeader, requestBodyJson)
        } getOrElse {
          Helpers.decodeDynamicRequest(requestHeader, requestBodyJson)
        }
      }
    }
    def safe(t:() => String): String = Try(t()).getOrElse("???")
  }

  def ?[IN <: Request[Body]](r: IN): Future[Response[Body]] = macro HyperBusMacro.ask[IN]

  def ask
    (r: Request[Body],
      requestEncoder: Encoder[Request[Body]],
      responseDecoder: ResponseDecoder): Future[Response[Body]] = {

    val outputDecoder = Helpers.decodeResponseWith(_:InputStream)(responseDecoder)
    underlyingBus.ask[Response[Body], Request[Body]](r.url, r, requestEncoder, outputDecoder) map {
      case throwable: Throwable ⇒ throw throwable
      case r: Response[Body] ⇒ r
    }
  }

  def on[IN <: Request[_ <: Body]] (groupName: Option[String] = None)
                                     (handler: (IN) => Future[Response[Body]]): String = macro HyperBusMacro.on[IN]

  def on[OUT <: Response[_ <: Body], IN <: Request[_ <: Body]]
  (url: String,
     method: String,
     contentType: Option[String],
     groupName: Option[String],
     requestDecoder: RequestDecoder)
    (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    // todo: handle service exceptions

    val routeKey = getRouteKey(url, groupName)
    val subRouteKey = getSubRouteKey(method, contentType)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        Some(subRouteKey),
        Subscription(handler.asInstanceOf[(Request[Body]) => SubscriptionHandlerResult[Response[Body]]], requestDecoder)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingHandler(routeKey)
        val uid = underlyingBus.on(url, groupName, uh.decoder)(uh.handler)
        underlyingSubscriptions += routeKey -> (uid, uh)
      }
      r
    }
  }

  def off(subscriptionId: String): Unit = {
    underlyingSubscriptions.synchronized {
      subscriptions.getRouteKeyById(subscriptionId) foreach { routeKey =>
        val cnt = subscriptions.get(routeKey).foldLeft(0){ (c, x) =>
          c + x._2.size
        }
        if (cnt <= 1) {
          underlyingSubscriptions.get(routeKey).foreach(k => underlyingBus.off(k._1))
        }
      }
      subscriptions.remove(subscriptionId)
    }
  }

  protected def getRouteKey(url: String, groupName: Option[String]) =
    groupName.map { url + "#" + _ } getOrElse url

  protected def getSubRouteKey(method: String, contentType: Option[String]) =
    contentType map (c => method + ":" + c) getOrElse method

  protected val log = LoggerFactory.getLogger(this.getClass)
}
