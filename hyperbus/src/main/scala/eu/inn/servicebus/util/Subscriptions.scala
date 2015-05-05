package eu.inn.servicebus.util

import java.util.concurrent.atomic.AtomicLong

import scala.collection.concurrent.TrieMap

case class SubscriptionWithId[T](subscriptionId:String, subscription:T)

class Subscriptions[T] {
  type SubscriptionMap = Map[String,IndexedSeq[SubscriptionWithId[T]]]
  protected val routes = new TrieMap[String, SubscriptionMap]
  protected val routeKeyById = new TrieMap[String, String]
  protected val indexGen = new AtomicLong(0)

  def get(routeKey: String) : SubscriptionMap = routes.getOrElse(routeKey, Map())
  def getRouteKeyById(subscriptionId:String) = routeKeyById.get(subscriptionId)

  def add(routeKey: String, subRouteKey: Option[String], subscription:T): String = {
    val subscriptionId = indexGen.incrementAndGet().toHexString
    val subscriberSeq = IndexedSeq(SubscriptionWithId(subscriptionId, subscription))
    val subRouteKeyStr = subRouteKey.getOrElse("")
    this.synchronized {
      val prev = routes.putIfAbsent(
        routeKey, Map(subRouteKeyStr -> subscriberSeq)
      )
      prev.map { existing =>
        val map =
          if (existing.contains(subRouteKeyStr)) {
            existing.map {
              kv => {
                (kv._1,
                if (kv._1 == subRouteKeyStr) {
                  kv._2 ++ subscriberSeq
                } else {
                  kv._2
                })
              }
            }
          }
          else {
            existing + (subRouteKeyStr -> subscriberSeq)
          }
        routes.put(routeKey, map)
      }
    }
    routeKeyById += subscriptionId -> routeKey
    subscriptionId
  }

  def remove(subscriptionId: String) = {
    this.synchronized {
      routeKeyById.get(subscriptionId).foreach {
        routeKey =>
          routes.get(routeKey).foreach { routeSubscribers =>
            val newTopicSubscribers = routeSubscribers.flatMap { kv =>
              val newSeq = kv._2.filter(s => s.subscriptionId != subscriptionId)
              if (newSeq.isEmpty)
                None
              else
                Some(kv._1, newSeq)
            }

            if (newTopicSubscribers.isEmpty)
              routes.remove(routeKey)
            else
              routes.put(routeKey, newTopicSubscribers)
          }
      }
    }
  }
}
