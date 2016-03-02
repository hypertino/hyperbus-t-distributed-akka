package eu.inn.hyperbus.transport.distributedakka

import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe, UnsubscribeAck}
import akka.pattern.pipe
import eu.inn.binders.dynamic.Null
import eu.inn.hyperbus.model.{Body, DynamicBody, DynamicRequest, Request}
import eu.inn.hyperbus.serialization.{StringSerializer, MessageDeserializer, RequestDeserializer}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Random
import scala.util.control.NonFatal

private[transport] class SubscriptionManager extends Actor with ActorLogging {
  // Map of (uri-pattern, groupName) -> (SubscriptionActor, ref-counter)
  val topicSubscriptions = mutable.Map[(String, Option[String]), (ActorRef, AtomicInteger)]()
  var workerCounter = 0l

  def receive: Receive = {
    case subscriptionCmd: SubscriptionCommand ⇒
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

private[transport] trait SubscriptionCommand {
  def requestMatcher: RequestMatcher

  def groupNameOption: Option[String]

  def inputDeserializer: RequestDeserializer[Request[Body]]

  def topic: (String, Option[String]) = {
    (requestMatcher.uri.map(_.pattern.specific) // currently only Specific url's are supported, todo: add Regex, Any, etc...
      .getOrElse(
      throw new IllegalArgumentException("requestMatcher.uri is empty")
    ), groupNameOption)
  }
}

private[transport] case class CommandSubscription(requestMatcher: RequestMatcher,
                                                  inputDeserializer: RequestDeserializer[Request[Body]],
                                                  handler: (Request[Body]) => Future[TransportResponse])
  extends SubscriptionCommand {
  def groupNameOption = None
}

private[transport] case class EventSubscription(requestMatcher: RequestMatcher,
                                                groupName: String,
                                                inputDeserializer: RequestDeserializer[Request[Body]],
                                                handler: (Request[Body]) => Future[Unit])
  extends SubscriptionCommand {

  def groupNameOption = Some(groupName)
}

private[transport] class DAkkaSubscription(val topic: (String, Option[String])) extends Subscription

private[transport] case class UnsubscribeCommand(subscription: Subscription)

private[transport] case object ReleaseTopicCommand

@SerialVersionUID(1L) case class HyperBusRequest(content: String)

@SerialVersionUID(1L) case class HyperBusResponse(content: String)

@SerialVersionUID(1L) case class HandlerIsNotFound(message: String) extends RuntimeException(message)

private[transport] abstract class SubscriptionActor extends Actor with ActorLogging {
  def topic: String
  def groupNameOption: Option[String]
  def handleRequest(request: Request[Body], matchedSubscriptions: Seq[SubscriptionCommand])

  import context._

  val mediator = DistributedPubSubEx(context.system).mediator

  // subscription handlers that are waiting to subscription of topic
  // Subscription (marker) -> (Command, Sender actor (waits for the reply)
  val handlersInProgress = mutable.Map[DAkkaSubscription, (SubscriptionCommand, ActorRef)]()
  val handlers = mutable.Map[DAkkaSubscription, SubscriptionCommand]()
  var subscribedToTopic = false

  log.debug(s"$self is subscribing to topic $topic @ groupName")
  mediator ! Subscribe(topic, Util.getUniqGroupName(groupNameOption), self)

  // todo: test empty group behavior

  def receive: Receive = {
    case ack: SubscribeAck ⇒
      log.debug(s"$self is subscribed to topic $topic @ groupName")
      handlersInProgress.foreach { case (subscription, (subscriptionCommand, replyToActor)) ⇒
        handlers += subscription -> subscriptionCommand
        replyToActor ! subscription
      }
      become(started)

    case cmd: SubscriptionCommand ⇒
      log.debug(s"$self accepted new handler: $cmd from $sender")
      handlersInProgress += new DAkkaSubscription(cmd.topic) →(cmd, sender)

    case cmd@UnsubscribeCommand(subscription: DAkkaSubscription) ⇒
      handlersInProgress.remove(subscription) match {
        case Some((_, actorRef)) ⇒
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
    case cmd: SubscriptionCommand ⇒
      log.debug(s"$self added new handler: $cmd")
      val pair = new DAkkaSubscription(cmd.topic) → cmd
      handlers += pair
      sender() ! pair._1

    case cmd@UnsubscribeCommand(subscription: DAkkaSubscription) ⇒
      handlers.remove(subscription) match {
        case Some(_) ⇒
          log.debug(s"$self removing subscription handler: ${cmd.subscription}")
          sender() ! subscription

        case None ⇒
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

    case HyperBusRequest(content) ⇒
      val inputBytes = new ByteArrayInputStream(content.getBytes(StringSerializer.defaultEncoding))
      var matchedSubscriptions: Seq[SubscriptionCommand] = Seq.empty

      try {
        val request = MessageDeserializer.deserializeRequestWith(inputBytes) { (requestHeader, jsonParser) ⇒
          val lookupMessage = DynamicRequest(requestHeader, DynamicBody(Null))
          matchedSubscriptions = handlers.values.filter(_.requestMatcher.matchMessage(lookupMessage)).toSeq

          // todo: fix or make a workaround without performance loss:
          // we assume here that all handlers provide the same deserializer
          // so currently it's not possible to subscribe to the same topic with different deserializers
          matchedSubscriptions.headOption.map(_.inputDeserializer(requestHeader, jsonParser)).getOrElse {
            DynamicRequest(requestHeader, jsonParser) // todo: this is ignored, just to skip body, ineffective but rare?
          }
        }

        handleRequest(request, matchedSubscriptions)
      }
      catch {
        case NonFatal(e) ⇒
          log.error(e, s"Can't handle request: $content")
      }
  }
}

private[transport] class CommandActor(val topic: String) extends SubscriptionActor {
  import context._

  def groupNameOption = None

  def handleRequest(request: Request[Body], matchedSubscriptions: Seq[SubscriptionCommand]) = {
    if (matchedSubscriptions.isEmpty) {
      log.error(s"$self: no handler is matched for a message: $request")
      sender() ! Status.Failure(HandlerIsNotFound(s"No handler were found for $request"))
    }
    else {
      val handler = getRandomElement(matchedSubscriptions).asInstanceOf[CommandSubscription].handler
      val futureResult = handler(request) map { case response ⇒
        HyperBusResponse(StringSerializer.serializeToString(response))
      }
      futureResult.onFailure {
        case NonFatal(e) ⇒
          log.error(e, s"Handler $handler is failed on request $request")
      }
      futureResult pipeTo sender
    }
  }
}

private[transport] class EventActor(val topic: String, groupName: String) extends SubscriptionActor {
  import context._
  def groupNameOption = Some(groupName)
  def handleRequest(request: Request[Body], matchedSubscriptions: Seq[SubscriptionCommand]) = {
    if (matchedSubscriptions.isEmpty) {
      log.debug(s"$self: no event handler is matched for a message: $request")
    }
    else {
      val selectedSubscriptions = matchedSubscriptions.groupBy(_.groupNameOption).map {
        case (group, subscriptions) ⇒
          getRandomElement(subscriptions)
      }

      selectedSubscriptions.foreach { case subscription: EventSubscription ⇒
        subscription.handler(request).onFailure {
          case NonFatal(e) ⇒
            log.error(e, s"Handler ${subscription.handler} is failed on request $request")
        }
      }
    }
  }
}