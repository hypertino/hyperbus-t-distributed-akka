package eu.inn.hyperbus.transport.kafkatransport

import java.io.ByteArrayInputStream
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ExecutorService, Executors, TimeUnit}

import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization.MessageDeserializer
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import kafka.consumer.{Consumer, ConsumerConfig, KafkaStream}
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.util.Random
import scala.util.control.NonFatal

private[transport] class TopicSubscription[REQ <: Request[Body]](
                         consumerProperties: Properties,
                         encoding: String,
                         threadCount: Int,
                         route: KafkaRoute,
                         groupName: String
                       ) {
  protected[this] val log = LoggerFactory.getLogger(this.getClass)
  protected[this] val subscriptionCounter = new AtomicLong(1)
  protected[this] val underlyingSubscriptions = TrieMap[Long, UnderlyingSubscription[REQ]]()
  protected[this] val subscriptionsByRequest = TrieMap[RequestMatcher, Vector[UnderlyingSubscription[REQ]]]()
  protected[this] val consumerId = this.hashCode().toHexString + "@" + groupName
  protected val randomGen = new Random()

  val consumer = {
    val props = consumerProperties.clone().asInstanceOf[Properties]
    val groupId = props.getProperty("group.id")
    val newGroupId = if (groupId != null) {
      groupId + "." + groupName
    }
    else {
      groupName
    }
    props.setProperty("group.id", newGroupId)
    Consumer.create(new ConsumerConfig(props))
  }
  @volatile var threadPool: ExecutorService = null

  def run(): Unit = {
    threadPool = Executors.newFixedThreadPool(threadCount)
    val consumerMap = consumer.createMessageStreams(Map(route.kafkaTopic → threadCount))
    val streams = consumerMap(route.kafkaTopic)

    streams.map { stream ⇒
      threadPool.submit(new Runnable {
        override def run(): Unit = consumeStream(stream)
      })
    }
  }

  def stop(duration: FiniteDuration): Unit = {
    consumer.commitOffsets
    consumer.shutdown()
    val t = threadPool
    if (t != null) {
      t.shutdown()
      if (duration.toMillis > 0) {
        try {
          t.awaitTermination(duration.toMillis, TimeUnit.MILLISECONDS)
        }
        catch {
          case t: InterruptedException ⇒ // .. do nothing
        }
      }
      threadPool = null
    }
  }

  def addUnderlying(subscription: UnderlyingSubscription[REQ]): Long = {
    val nextId = subscriptionCounter.incrementAndGet()
    this.underlyingSubscriptions.put(nextId, subscription)
    this.subscriptionsByRequest.get(subscription.requestMatcher) match {
      case Some(vector) ⇒
        subscriptionsByRequest.put(subscription.requestMatcher, vector :+ subscription)
      case None ⇒
        subscriptionsByRequest.put(subscription.requestMatcher, Vector(subscription))
    }
    log.info(s"+1 handler on consumer #$consumerId on topic ${route.kafkaTopic} -> ${subscription.requestMatcher}")
    nextId
  }

  def removeUnderlying(id: Long): Boolean = {
    this.underlyingSubscriptions.remove(id).map { subscription ⇒
      this.subscriptionsByRequest.get(subscription.requestMatcher) match {
        case Some(vector) ⇒
          val newVector = vector.filterNot(_ == subscription)
          if (newVector.isEmpty) {
            this.subscriptionsByRequest.remove(subscription.requestMatcher)
          } else {
            subscriptionsByRequest.put(subscription.requestMatcher, newVector)
          }
        case None ⇒
          // in theory this shouldn't happen
      }
    }
    this.underlyingSubscriptions.isEmpty
  }

  protected def consumeMessage(next: kafka.message.MessageAndMetadata[Array[Byte], Array[Byte]]): Unit = {
    val message = next.message()
    lazy val messageString = new String(message, encoding)
    if (log.isTraceEnabled) {
      log.trace(s"Consumer $consumerId got message from kafka ${next.topic}/${next.partition}${next.offset}: $messageString")
    }
    try {
      var atLeastOneHandler = false

      subscriptionsByRequest.values.foreach { underlyingVector ⇒
        val underlying = if(underlyingVector.size > 1) {
          underlyingVector(randomGen.nextInt(underlyingVector.size))
        } else {
          underlyingVector.head
        }

        val inputBytes = new ByteArrayInputStream(message)
        val input = MessageDeserializer.deserializeRequestWith(inputBytes)(underlying.inputDeserializer)
        if (underlying.requestMatcher.matchMessage(input)) {
          println(input + " - " + underlying)
          // todo: test order of matching?
          underlying.observer.onNext(input)
          atLeastOneHandler = true
        }
      }

      if (!atLeastOneHandler && log.isTraceEnabled) {
        log.trace(s"Consumer #$consumerId. Skipped message from partiton#${next.partition}: $messageString")
      }
    }
    catch {
      case NonFatal(e) ⇒
        log.error(s"Consumer #$consumerId can't deserialize message from partiton#${next.partition}: $messageString", e)
    }
  }

  protected def consumeStream(stream: KafkaStream[Array[Byte], Array[Byte]]): Unit = {
    log.info(s"Starting consumer #$consumerId on topic ${route.kafkaTopic} -> ${underlyingSubscriptions.values.map(_.requestMatcher)}")
    try {
      val iterator = stream.iterator()
      while (iterator.hasNext()) {
        val next = iterator.next()
        consumeMessage(next)
      }
      log.info(s"Stopping consumer #$consumerId on topic ${route.kafkaTopic}")
    }
    catch {
      case NonFatal(t) ⇒
        log.error(s"Consumer #$consumerId failed", t)
    }
  }
}
