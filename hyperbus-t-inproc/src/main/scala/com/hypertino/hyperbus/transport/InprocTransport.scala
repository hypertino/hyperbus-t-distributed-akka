package com.hypertino.hyperbus.transport

import com.typesafe.config.Config
import com.hypertino.hyperbus.model.{Body, Request}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.transport.inproc.{InprocSubscription, InprocSubscriptionHandler, InprocSubscriptionHandler$, SubKey}
import com.hypertino.hyperbus.util.ConfigUtils._
import com.hypertino.hyperbus.util.Subscriptions
import org.slf4j.LoggerFactory
import rx.lang.scala.Observer

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}

class InprocTransport(serialize: Boolean = false)
                     (implicit val executionContext: ExecutionContext) extends ClientTransport with ServerTransport {

  def this(config: Config) = this(
    serialize = config.getOptionBoolean("serialize").getOrElse(false)
  )(
    scala.concurrent.ExecutionContext.global // todo: configurable ExecutionContext like in akka?
  )

  protected val subscriptions = new Subscriptions[SubKey, InprocSubscriptionHandler[_]]
  protected val log = LoggerFactory.getLogger(this.getClass)

  // todo: refactor this method, it's awful
  protected def _ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse], isPublish: Boolean): Future[_] = {
    val resultPromise = Promise[TransportResponse]
    var handled = false
    //var result: Future[OUT] = null

    // todo: filter is redundant for inproc?
    subscriptions.get(message.uri.pattern.specific).subRoutes.filter { subRoute ⇒
      subRoute._1.requestMatcher.matchMessage(message)
    }.foreach {
      case (subKey, subscriptionList) =>
        val subscriber = subscriptionList.getRandomSubscription
        handled = subscriber.handleCommandOrEvent(serialize,subKey,message,outputDeserializer,isPublish,resultPromise) || handled
    }

    if (!handled) {
      Future.failed {
        new NoTransportRouteException(s"Handler is not found for ${message.uri} with header matchers: ${message.headers}")
      }
    }
    else if (isPublish) {
      Future.successful {
        new PublishResult {
          def sent = Some(true)

          def offset = None

          override def toString = s"PublishResult(sent=Some(true),offset=None)"
        }
      }
    } else {
      resultPromise.future
    }
  }

  override def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse] = {
    _ask(message, outputDeserializer, isPublish = false).asInstanceOf[Future[TransportResponse]]
  }

  override def publish(message: TransportRequest): Future[PublishResult] = {
    _ask(message, null, isPublish = true).asInstanceOf[Future[PublishResult]]
  }

  override def onCommand[REQ <: Request[Body]](matcher: RequestMatcher,
                         inputDeserializer: RequestDeserializer[REQ])
                        (handler: (REQ) => Future[TransportResponse]): Future[Subscription] = {

    if (matcher.uri.isEmpty)
      throw new IllegalArgumentException("requestMatcher.uri is empty")

    val id = subscriptions.add(
      matcher.uri.get.pattern.specific, // currently only Specific url's are supported, todo: add Regex, Any, etc...
      SubKey(None, matcher),
      InprocSubscriptionHandler(inputDeserializer, Left(handler))
    )
    Future.successful(InprocSubscription(id))
  }

  override def onEvent[REQ <: Request[Body]](matcher: RequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[REQ],
                       observer: Observer[REQ]): Future[Subscription] = {
    if (matcher.uri.isEmpty)
      throw new IllegalArgumentException("requestMatcher.uri")

    val id = subscriptions.add(
      matcher.uri.get.pattern.specific, // currently only Specific url's are supported, todo: add Regex, Any, etc...
      SubKey(Some(groupName), matcher),
      InprocSubscriptionHandler(inputDeserializer, Right(observer))
    )
    Future.successful(InprocSubscription(id))
  }

  override def off(subscription: Subscription): Future[Unit] = {
    subscription match {
      case i: InprocSubscription ⇒
        Future {
          subscriptions.remove(i.id)
        }
      case other ⇒
        Future.failed {
          new ClassCastException(s"InprocSubscription expected but ${other.getClass} is received")
        }
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    subscriptions.clear()
    Future.successful(true)
  }
}
