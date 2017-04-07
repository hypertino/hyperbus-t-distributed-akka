package com.hypertino.hyperbus.transport.distributedakka

import java.io.Reader
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.pattern.pipe
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe, UnsubscribeAck}
import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.{Body, DynamicRequest, EmptyBody, ErrorBody, HyperbusError, InternalServerError, Request, RequestBase, RequestHeaders, ResponseBase}
import com.hypertino.hyperbus.serialization.{MessageReader, RequestDeserializer}
import com.hypertino.hyperbus.transport.api.CommandEvent
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.HyperbusSubscription
import monix.eval.Task

import scala.collection.mutable
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

private[transport] class SubscriptionManager extends Actor with ActorLogging {
  // Map of (serviceAddress, groupName) -> (SubscriptionActor, ref-counter)
  val topicSubscriptions = mutable.Map[(String, Option[String]), (ActorRef, AtomicInteger)]()
  var workerCounter = 0l

  def receive: Receive = {
    case subscriptionCmd: DAkkaSubscription ⇒
      val topic = subscriptionCmd.topic
      topicSubscriptions.get(topic) match {
        case Some((subscriptionActorRef, refCounter)) ⇒
          refCounter.incrementAndGet()
          subscriptionActorRef forward subscriptionCmd

        case None ⇒
          workerCounter += 1
          val newSubscriptionActorRef = if (topic._2.isEmpty) {
            context.system.actorOf(Props(classOf[CommandActor], topic._1), s"d-akka-cmdwrkr-$workerCounter")
          }
          else {
            context.system.actorOf(Props(classOf[EventActor], topic._1, topic._2.get), s"d-akka-evntwrkr-$workerCounter")
          }
          topicSubscriptions.put(topic, (newSubscriptionActorRef, new AtomicInteger(1)))
          newSubscriptionActorRef forward subscriptionCmd
      }

    case cmd@UnsubscribeCommand(subscription: DAkkaSubscription) ⇒
      topicSubscriptions.get(subscription.topic) match {
        case Some((subscriptionActorRef, refCounter)) ⇒
          subscriptionActorRef forward cmd
          if (refCounter.decrementAndGet() == 0) {
            subscriptionActorRef ! ReleaseTopicCommand
            topicSubscriptions.remove(subscription.topic)
          }
        case None ⇒
          // todo: respond with fail instead of log
          log.error(s"Invalid unsubscribe command: $cmd. Topic is not found")
      }
  }
}

private[transport] trait DAkkaSubscription {
  def requestMatcher: RequestMatcher

  def groupNameOption: Option[String]

  def inputDeserializer: RequestDeserializer[RequestBase]

  def topic: (String, Option[String]) = {
    (requestMatcher
      .serviceAddressMatcher
      .getOrElse(throw new IllegalArgumentException("requestMatcher.serviceAddress is empty"))
      .specific // currently only Specific url's are supported, todo: add Regex, Any, etc...
    , groupNameOption)
  }

  def handleRequest(sender: ActorRef)(implicit request: RequestBase, context: ActorContext)
}

private[transport] class CommandHyperbusSubscription(val requestMatcher: RequestMatcher,
                                                     val inputDeserializer: RequestDeserializer[RequestBase],
                                                     val subscriptionManager: ActorRef)
                                                    (implicit scheduler: monix.execution.Scheduler)
  extends HyperbusSubscription[CommandEvent[RequestBase]] with DAkkaSubscription {

  override def groupNameOption = None

  override def add(): Unit = {
    // todo: wait
    subscriptionManager ! this
  }

  override def remove(): Unit = {
    // todo: wait
    subscriptionManager ! UnsubscribeCommand(this)
  }


  override def handleRequest(sender: ActorRef)(implicit request: RequestBase, context: ActorContext): Unit = {
    Task.create[ResponseBase] { (_, callback) ⇒
      val command = CommandEvent(request, callback)
      publish(command).runAsync
    }.onErrorRecover {
      case e: HyperbusError[ErrorBody] ⇒ e
      case NonFatal(e) ⇒ InternalServerError(ErrorBody(e.toString)) // todo: log exception & errorId
    }.map(r ⇒ HyperbusResponse(r.serializeToString)).runAsync pipeTo sender
  }
}

private[transport] class EventHyperbusSubscription(val requestMatcher: RequestMatcher,
                                                   val group: String,
                                                   val inputDeserializer: RequestDeserializer[RequestBase],
                                                   val subscriptionManager: ActorRef)
                                                  (implicit scheduler: monix.execution.Scheduler)
  extends HyperbusSubscription[RequestBase] with DAkkaSubscription {

  override def groupNameOption = Some(group)

  override def add(): Unit = {
    // todo: wait
    subscriptionManager ! this
  }

  override def remove(): Unit = {
    // todo: wait
    subscriptionManager ! UnsubscribeCommand(this)
  }

  override def handleRequest(sender: ActorRef)(implicit request: RequestBase, context: ActorContext): Unit = {
    publish(request)
  }
}

private[transport] case class UnsubscribeCommand(subscription: DAkkaSubscription)

private[transport] case object ReleaseTopicCommand

@SerialVersionUID(1L) case class HyperbusRequest(content: String)

@SerialVersionUID(1L) case class HyperbusResponse(content: String)

@SerialVersionUID(1L) case class HandlerIsNotFound(message: String) extends RuntimeException(message)

private[transport] abstract class SubscriptionActor extends Actor with ActorLogging {
  def topic: String
  def groupNameOption: Option[String]
  def handleRequest(request: Request[Body], matchedSubscriptions: Seq[DAkkaSubscription])

  import context._

  val mediator = DistributedPubSub(context.system).mediator

  // subscription handlers that are waiting to subscription of topic
  // Subscription (marker) -> (Command, Sender actor (waits for the reply)
  val handlersInProgress = mutable.Map[DAkkaSubscription, ActorRef]()
  val handlers = mutable.Set[DAkkaSubscription]()
  var subscribedToTopic = false

  log.debug(s"$self is subscribing to topic $topic @ groupName")
  mediator ! Subscribe(topic, Util.getUniqGroupName(groupNameOption), self)

  // todo: test empty group behavior

  def receive: Receive = {
    case ack: SubscribeAck ⇒
      log.debug(s"$self is subscribed to topic $topic @ groupName")
      handlersInProgress.foreach { case (subscription, replyToActor) ⇒
        handlers += subscription
        replyToActor ! subscription
      }
      become(started)

    case cmd: DAkkaSubscription ⇒
      log.debug(s"$self accepted new handler: $cmd from $sender")
      handlersInProgress += cmd → sender

    case cmd@UnsubscribeCommand(subscription: DAkkaSubscription) ⇒
      handlersInProgress.remove(subscription) match {
        case Some(actorRef) ⇒
          log.debug(s"$self removing handler (in-progress): ${cmd.subscription}")
          actorRef ! Status.Failure(new RuntimeException(s"Subscription (in-progress) is canceled by off method"))
          sender() ! subscription

        case None ⇒
          log.error(s"$self is not found subscription handler (in-progress) to remove: ${cmd.subscription}")
          sender() ! Status.Failure(new RuntimeException(s"Subscription (in-progress) is not found: ${cmd.subscription}"))
      }

    case ReleaseTopicCommand ⇒
      log.debug(s"$self is unsubscribing from topic $topic @ groupName")
      mediator ! Unsubscribe(topic, Util.getUniqGroupName(groupNameOption), self)

    case UnsubscribeAck(unsubscribe) ⇒
      log.debug(s"$self is stopping...")
      context.stop(self)
  }

  def getRandomElement[T](seq: Seq[T]): T = {
    val random = new Random()
    if (seq.size > 1)
      seq(random.nextInt(seq.size))
    else
      seq.head
  }

  def started: Receive = {
    case subscription: DAkkaSubscription ⇒
      log.debug(s"$self added new handler: $subscription")
      handlers += subscription
      sender() ! subscription

    case cmd@UnsubscribeCommand(subscription: DAkkaSubscription) ⇒
      if (handlers.remove(subscription)) {
        log.debug(s"$self removing subscription handler: ${cmd.subscription}")
        sender() ! subscription
      } else {
        log.error(s"$self is not found subscription handler to remove: ${cmd.subscription}")
        sender() ! Status.Failure(new RuntimeException(s"Subscription is not found: ${cmd.subscription}"))
      }

    case ReleaseTopicCommand ⇒
      if (handlers.isEmpty) {
        log.debug(s"$self is unsubscribing from topic $topic @ groupName")
      }
      else {
        log.error(s"$self is unsubscribing from topic $topic @ groupName while having handlers: $handlers")
      }
      mediator ! Unsubscribe(topic, Util.getUniqGroupName(groupNameOption), self)

    case UnsubscribeAck(unsubscribe) ⇒
      log.debug(s"$self is stopping...")
      context.stop(self)

    case HyperbusRequest(content) ⇒
      var matchedSubscriptions: Seq[DAkkaSubscription] = Seq.empty

      try {
        val request = MessageReader.from(content, { (reader: Reader, obj: Obj) ⇒
          val lookupMessage = DynamicRequest(EmptyBody, RequestHeaders(obj))
          // todo: use fuzzyIndex ?
          matchedSubscriptions = handlers.filter(_.requestMatcher.matchMessage(lookupMessage)).toSeq

          // todo: fix or make a workaround without performance loss:
          // we assume here that all handlers provide the same deserializer
          // so currently it's not possible to subscribe to the same topic with different deserializers
          matchedSubscriptions.headOption.map(_.inputDeserializer(reader, obj)).getOrElse {
            DynamicRequest(reader, obj) // todo: this is ignored, just to skip body, ineffective but rare?
          }
        })

        handleRequest(request, matchedSubscriptions)
      }
      catch {
        case NonFatal(e) ⇒
          log.error(e, s"Can't handle request: $content")
      }
  }
}

private[transport] class CommandActor(val topic: String) extends SubscriptionActor {
  def groupNameOption = None

  def handleRequest(request: Request[Body], matchedSubscriptions: Seq[DAkkaSubscription]) = {
    if (matchedSubscriptions.isEmpty) {
      log.error(s"$self: no handler is matched for a message: $request")
      sender() ! Status.Failure(HandlerIsNotFound(s"No handler were found for $request"))
    }
    else {
      getRandomElement(matchedSubscriptions).handleRequest(sender)(request, context)
    }
  }
}

private[transport] class EventActor(val topic: String, groupName: String) extends SubscriptionActor {
  def groupNameOption = Some(groupName)
  def handleRequest(request: Request[Body], matchedSubscriptions: Seq[DAkkaSubscription]) = {
    if (matchedSubscriptions.isEmpty) {
      log.debug(s"$self: no event handler is matched for a message: $request")
    }
    else {
      val selectedSubscriptions = matchedSubscriptions.groupBy(_.groupNameOption).map {
        case (group, subscriptions) ⇒
          getRandomElement(subscriptions)
      }

      selectedSubscriptions.foreach(_.handleRequest(sender)(request, context))
    }
  }
}

//  override def onCommand[REQ <: Request[Body]](requestMatcher: RequestMatcher,
//                         inputDeserializer: RequestDeserializer[REQ])
//                        (handler: (REQ) => Future[TransportResponse]): Future[Subscription] = {
//
//
//    (subscriptionManager ? CommandSubscription(requestMatcher, inputDeserializer, handler)).mapTo[Subscription]
//  }
//
//  override def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
//                       groupName: String,
//                       inputDeserializer: RequestDeserializer[REQ],
//                       subscriber: Observer[REQ]): Future[Subscription] = {
//    (subscriptionManager ? EventSubscription(requestMatcher, groupName, inputDeserializer, subscriber)).mapTo[Subscription]
//  }
//
//  override def off(subscription: Subscription): Future[Unit] = {
//    import actorSystem._
//    (subscriptionManager ? UnsubscribeCommand(subscription)) map { _ ⇒
//      {} // converts to Future[Unit]
//    }
//  }
