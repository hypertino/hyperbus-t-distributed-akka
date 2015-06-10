package eu.inn.servicebus.util

import java.util.concurrent.atomic.AtomicLong

import scala.collection.concurrent.TrieMap
import scala.util.Random

case class SubscriptionWithId[T](subscriptionId: String, subscription: T)

class SubscriptionList[T](private val seq: IndexedSeq[SubscriptionWithId[T]]) {
  def this(init: SubscriptionWithId[T]) = this(IndexedSeq[SubscriptionWithId[T]](init))

  def ++(other: SubscriptionList[T]): SubscriptionList[T] = {
    new SubscriptionList(seq ++ other.seq)
  }

  def isEmpty = seq.isEmpty

  def filterNot(p: SubscriptionWithId[T] => Boolean) = new SubscriptionList(seq.filterNot(p))

  def size = seq.size

  def getRandomSubscription: T = if (seq.size > 1)
    seq(SubscriptionList.getNextRandomInt(seq.size)).subscription
  else
    seq.head.subscription

  def foreach(code:T ⇒ Unit): Unit = seq.foreach(x ⇒ code(x.subscription))
  def map[O](code:T ⇒ O): Iterable[O] = seq.map(x ⇒ code(x.subscription))
}

object SubscriptionList {
  protected val randomGen = new Random()

  private[util] def getNextRandomInt(max: Int) = randomGen.nextInt(max)
}

class Subscriptions[K, T] {

  case class SubscriptionMap(subRoutes: Map[K, SubscriptionList[T]]) extends ComplexElement[SubscriptionMap, String] {
    override def upsert(upsertPart: SubscriptionMap): SubscriptionMap = SubscriptionMap(
      subRoutes ++ upsertPart.subRoutes map {
        case (k, v) => k -> subRoutes.get(k).map {
          existing => existing ++ v
        }.getOrElse {
          v
        }
      }
    )

    override def remove(removeSubscriptionId: String): SubscriptionMap = SubscriptionMap(
      subRoutes.flatMap {
        case (k, v) =>
          val newV = v.filterNot(_.subscriptionId == removeSubscriptionId)
          if (newV.isEmpty)
            None
          else
            Some(k -> newV)
      }
    )

    override def isEmpty: Boolean = subRoutes.isEmpty
  }

  protected val routes = new ComplexTrieMap[String, String, SubscriptionMap]
  protected val routeKeyById = new TrieMap[String, String]
  protected val idCounter = new AtomicLong(0)

  def get(routeKey: String): SubscriptionMap = routes.getOrElse(routeKey, SubscriptionMap(Map()))

  def getRouteKeyById(subscriptionId: String) = routeKeyById.get(subscriptionId)

  def add(routeKey: String, subKey: K, subscription: T): String = {
    val subscriptionId = idCounter.incrementAndGet().toHexString
    val subscriptionValue = new SubscriptionList(SubscriptionWithId(subscriptionId, subscription))
    val s = SubscriptionMap(Map(subKey -> subscriptionValue))
    routes.upsert(routeKey, s)
    routeKeyById += subscriptionId -> routeKey
    subscriptionId
  }

  def remove(subscriptionId: String) = {
    routeKeyById.get(subscriptionId).foreach {
      routeKey =>
        routes.remove(routeKey, subscriptionId)
        routeKeyById.remove(subscriptionId)
    }
  }

  def foreach(code:T ⇒ Unit): Unit = routes.foreach(_._2.subRoutes.foreach(_._2.foreach(code)))
  def map[O](code:T ⇒ O): Iterable[O] = routes.map(_._2.subRoutes.flatMap(_._2.map(code))).flatten

  def clear(): Unit = {
    routes.clear()
    routeKeyById.clear()
  }
}
