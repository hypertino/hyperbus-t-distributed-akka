package eu.inn.hyperbus.transport.distributedakka

import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe, UnsubscribeAck}
import akka.pattern.pipe
import eu.inn.binders.dynamic.Null
import eu.inn.hyperbus.model.{Body, DynamicBody, DynamicRequest, Request}
import eu.inn.hyperbus.serialization.{StringDeserializer, MessageDeserializer, RequestDeserializer}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher
import eu.inn.hyperbus.util.StringSerializer

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Random
import scala.util.control.NonFatal

private [transport] class SubscriptionManager extends Actor with ActorLogging {
  val topicSubscriptions = mutable.Map[(String, Option[String]), (ActorRef,AtomicInteger)]()
  var workerCounter = 0l

  def receive: Receive = {
    case subscriptionCmd: SubscriptionCommand ⇒
      val topic = subscriptionCmd.topic
      topicSubscriptions.get(topic) match {
        case Some((actorRef,counter)) ⇒
          counter.incrementAndGet()
          actorRef forward subscriptionCmd

        case None ⇒
          workerCounter += 1
          val newActorRef = context.system.actorOf(Props(classOf[SubscriptionActor], topic), s"d-akka-wrkr-$workerCounter")
          topicSubscriptions.put(topic, (newActorRef,new AtomicInteger(1)))
          newActorRef forward subscriptionCmd
      }

    case cmd @ UnsubscribeCommand(subscription : DAkkaSubscription) ⇒
      topicSubscriptions.get(subscription.topic) match {
        case Some((actor,counter)) ⇒
          actor forward cmd
          if (counter.decrementAndGet() == 0) {
            actor ! ReleaseTopicCommand
            topicSubscriptions.remove(subscription.topic)
          }
        case None ⇒
          log.error(s"Invalid unsubscribe command: $cmd. Topic is not found")
      }
  }
}

private[transport] trait SubscriptionCommand {
  def requestMatcher: TransportRequestMatcher
  def groupNameOption: Option[String]
  def inputDeserializer: RequestDeserializer[Request[Body]]
  def topic: (String, Option[String]) = {
    (requestMatcher.uri.map(_.pattern.specific) // currently only Specific url's are supported, todo: add Regex, Any, etc...
      .getOrElse(
      throw new IllegalArgumentException("requestMatcher.uri is empty")
    ),groupNameOption)
  }
}
private[transport] case class CommandSubscription(requestMatcher: TransportRequestMatcher,
                                                  inputDeserializer: RequestDeserializer[Request[Body]],
                                                  handler: (Request[Body]) => Future[TransportResponse])
  extends SubscriptionCommand {
  def groupNameOption = None
}

private[transport] case class EventSubscription(requestMatcher: TransportRequestMatcher,
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

private [transport] class SubscriptionActor(topic: (String, Option[String])) extends Actor with ActorLogging {
  import context._
  val mediator = DistributedPubSubEx(context.system).mediator
  val handlersInProgress = mutable.Map[DAkkaSubscription, (SubscriptionCommand, ActorRef)]()
  val handlers = mutable.Map[DAkkaSubscription, SubscriptionCommand]()
  var subscribedToTopic = false

  log.debug(s"$self is subscribing to topic ${topic._1} @ ${topic._2}")
  mediator ! Subscribe(topic._1, Util.getUniqGroupName(topic._2), self) // todo: test empty group behavior

  def receive: Receive = {
    case ack: SubscribeAck ⇒
      log.debug(s"$self is subscribed to topic ${topic._1} @ ${topic._2}")
      handlersInProgress.foreach { case (subscription, (subscriptionCommand, actoref)) ⇒
        handlers += subscription -> subscriptionCommand
        actoref ! subscription
      }
      become(started)

    case cmd: SubscriptionCommand ⇒
      log.debug(s"$self accepted new handler: $cmd from $sender")
      handlersInProgress += new DAkkaSubscription(cmd.topic) → (cmd, sender)

    case cmd @ UnsubscribeCommand(subscription : DAkkaSubscription) ⇒
      handlersInProgress.remove(subscription) match {
        case Some((_, actorRef)) ⇒
          log.debug(s"$self removing handler (in-progress): ${cmd.subscription}")
          Future.failed(new RuntimeException(s"Handler subscription is canceled by off method")) pipeTo actorRef
          sender ! cmd.subscription

        case None ⇒
          log.error(s"$self is not found handler (in-progress) to remove: ${cmd.subscription}")
          Future.failed(new RuntimeException(s"Handler is not found: ${cmd.subscription}")) pipeTo sender()
      }

    case ReleaseTopicCommand ⇒
      log.debug(s"$self is unsubscribing from topic ${topic._1} @ ${topic._2}")
      mediator ! Unsubscribe(topic._1, Util.getUniqGroupName(topic._2), self)

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

    case cmd @ UnsubscribeCommand(subscription : DAkkaSubscription) ⇒
      handlers.remove(subscription) match {
        case Some(_) ⇒
          log.debug(s"$self removing handler: ${cmd.subscription}")
          sender() ! cmd.subscription

        case None ⇒
          log.error(s"$self is not found handler to remove: ${cmd.subscription}")
          Future.failed(new RuntimeException(s"Handler is not found: ${cmd.subscription}")) pipeTo sender()
      }

    case ReleaseTopicCommand ⇒
      if (handlers.isEmpty) {
        log.debug(s"$self is unsubscribing from topic ${topic._1} @ ${topic._2}")
      }
      else {
        log.error(s"$self is unsubscribing from topic ${topic._1} @ ${topic._2} while having handlers: $handlers")
      }
      mediator ! Unsubscribe(topic._1, Util.getUniqGroupName(topic._2), self)

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
          matchedSubscriptions.headOption.map(_.inputDeserializer(requestHeader, jsonParser)).getOrElse(lookupMessage)
        }

        if (matchedSubscriptions.isEmpty) {
          log.error(s"$self: no handler is matched for a message: $request")
          Future.failed(HandlerIsNotFound(s"No handler were found for $request")) pipeTo sender
        }
        else {
          val selectedSubscriptions = matchedSubscriptions.groupBy(_.groupNameOption).map {
            case (group, subscriptions) ⇒
              group → getRandomElement(subscriptions)
          }

          selectedSubscriptions.foreach {
            case (None, CommandSubscription(_, _, handler)) ⇒
              val futureResult = handler(request) map { case response ⇒
                HyperBusResponse(StringSerializer.serializeToString(response))
              }
              futureResult.onFailure {
                case NonFatal(e) ⇒
                  log.error(e, s"Handler $handler is failed on request $request")
              }
              futureResult pipeTo sender

            case (Some(groupName), EventSubscription(_, _, _, handler)) ⇒
              handler(request).onFailure {
                case NonFatal(e) ⇒
                  log.error(e, s"Handler $handler is failed on request $request")
              }
          }
        }
      }
      catch {
        case NonFatal(e) ⇒
          log.error(e, s"Can't handler request: $content")
      }
  }
}

private [transport] class NoRouteWatcher extends Actor {
  import context._
  system.eventStream.subscribe(self, classOf[DeadLetter])

  override def receive: Receive = {
    case DeadLetter( message : HyperBusRequest, messageSender, recipient ) ⇒
      Future.failed(new NoTransportRouteException(recipient.toString())) pipeTo messageSender
  }
}

/*
private[transport] trait Command

private[transport] case class Subscription[OUT, IN <: TransportRequest](requestMatcher: TransportRequestMatcher,
                                                                        groupName: Option[String],
                                                                        inputDeserializer: Deserializer[IN],
                                                                        exceptionSerializer: Serializer[Throwable],
                                                                        handler: (IN) => Future[OUT]) {
  def topic = {
    requestMatcher.uri.map(_.pattern.specific) // currently only Specific url's are supported, todo: add Regex, Any, etc...
      .getOrElse(
        throw new IllegalArgumentException("requestMatcher.uri is empty")
      )
  }
}

private[transport] case class Start[OUT, IN <: TransportRequest](id: String, subscription: Subscription[OUT, IN]) extends Command

private [transport] object Stop

private[transport] abstract class ServerActor[OUT, IN <: TransportRequest] extends Actor {
  protected[this] val mediator = DistributedPubSubEx(context.system).mediator
  protected[this] var subscription: Subscription[OUT, IN] = null
  protected[this] val log = LoggerFactory.getLogger(getClass)

  override def receive: Receive = handleStart orElse handleStop

  def handleStart: Receive = {
    case start: Start[OUT, IN] ⇒
      subscription = start.subscription
      log.debug(s"$self is subscribing to topic ${subscription.topic}/${subscription.groupName}")
      mediator ! Subscribe(subscription.topic, Util.getUniqGroupName(subscription.groupName), self) // todo: test empty group behavior

    case ack: SubscribeAck ⇒
      log.debug(s"$self is subscribed to topic ${subscription.topic}/${subscription.groupName}")
      context become (start orElse handleStop)
  }

  def handleStop: Receive = {
    case Stop ⇒
      log.debug(s"$self is unsubscribing from topic ${subscription.topic}/${subscription.groupName}")
      mediator ! Unsubscribe(subscription.topic, self)

    case UnsubscribeAck(unsubscribe) ⇒
      log.debug(s"$self is stopping...")
      context.stop(self)
  }

  def start: Receive

  protected def handleException(e: Throwable, sendReply: Boolean): Option[String] = {
    val msg = try {
      val outputBytes = new ByteArrayOutputStream()
      subscription.exceptionSerializer(e, outputBytes)
      Some(outputBytes.toString(Util.defaultEncoding))
    } catch {
      case NonFatal(e2) ⇒
        log.error("Can't serialize exception: " + e, e2)
        None
    }

    if (sendReply) {
      msg.foreach { s ⇒
        import context._
        Future.successful(s) pipeTo context.sender
      }
    }

    msg
  }

  protected def decodeMessage(input: String, sendReply: Boolean) = {
    try {
      val inputBytes = new ByteArrayInputStream(input.getBytes(Util.defaultEncoding))
      Some(subscription.inputDeserializer(inputBytes))
    }
    catch {
      case NonFatal(e) ⇒
        handleException(e, sendReply)
        None
    }
  }
}

private[transport] class ProcessServerActor[IN <: TransportRequest] extends ServerActor[TransportResponse, IN] {

  import context._
  import eu.inn.hyperbus.util.LogUtils._

  def start: Receive = {
    case input: String ⇒
      if (logMessages && log.isTraceEnabled) {
        log.trace(Map("requestId" → input.hashCode.toHexString,
          "subscriptionId" → subscription.handler.hashCode.toHexString), s"hyperBus ~> $input")
      }

      decodeMessage(input, sendReply = true) map { inputMessage ⇒
        val result = subscription.handler(inputMessage) // todo: test result with partitonArgs?
      val futureMessage = result.map { out ⇒
          val outputBytes = new ByteArrayOutputStream()
          out.serialize(outputBytes)
          outputBytes.toString(Util.defaultEncoding)
        } recover {
          case NonFatal(e) ⇒ handleException(e, sendReply = false).getOrElse(throw e) // todo: test this scenario
        }
        if (logMessages && log.isTraceEnabled) {
          futureMessage map { s ⇒
            log.trace(Map("requestId" → input.hashCode.toHexString,
              "subscriptionId" → subscription.handler.hashCode.toHexString), s"hyperBus <~(R)~ $s")
            s
          } pipeTo sender
        }
        else {
          futureMessage pipeTo sender
        }
      }
  }
}

private[transport] class SubscribeServerActor[IN <: TransportRequest] extends ServerActor[Unit, IN] {

  import context._
  import eu.inn.hyperbus.util.LogUtils._

  def start: Receive = {
    case input: String ⇒
      if (logMessages && log.isTraceEnabled) {
        log.trace(Map("subscriptionId" → subscription.handler.hashCode.toHexString), s"hyperBus |> $input")
      }
      decodeMessage(input, sendReply = false) map { inputMessage ⇒
        subscription.handler(inputMessage).recover {
          // todo: test result with partitonArgs?
          case NonFatal(e) ⇒ log.error(Map("subscriptionId" → subscription.handler.hashCode.toHexString),
            "Subscriber handler failed", e)
        }
      }
  }
}
*/