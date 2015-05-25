package eu.inn.servicebus.util

import java.util.concurrent.atomic.AtomicLong

import scala.collection.concurrent.TrieMap

case class SubscriptionWithId[T](subscriptionId:String, subscription:T)


class Subscriptions[T] {

  case class SubscriptionMap(subRoutes: Map[String,IndexedSeq[SubscriptionWithId[T]]]) extends ComplexElement[SubscriptionMap, String] {
    override def upsert(upsertPart: SubscriptionMap): SubscriptionMap = SubscriptionMap(
      subRoutes ++ upsertPart.subRoutes map {
        case(k,v) => k -> subRoutes.get(k).map {
          existing => existing ++ v
        }.getOrElse {
          v
        }
      }
    )
    override def remove(removeSubscriptionId: String): SubscriptionMap = SubscriptionMap(
      subRoutes.flatMap {
        case (k,v) =>
          val newV = v.filterNot(_.subscriptionId == removeSubscriptionId)
          if (newV.isEmpty)
            None
          else
            Some(k -> newV)
      }
    )
    override def isEmpty: Boolean = subRoutes.isEmpty
  }

  protected val routes = new ComplexTrieMap[String,String,SubscriptionMap]
  protected val routeKeyById = new TrieMap[String, String]
  protected val indexGen = new AtomicLong(0)

  def get(routeKey: String) : SubscriptionMap = routes.getOrElse(routeKey, SubscriptionMap(Map()))
  def getRouteKeyById(subscriptionId:String) = routeKeyById.get(subscriptionId)

  def add(routeKey: String, subRouteKey: Option[String], subscription:T): String = {
    val subscriptionId = indexGen.incrementAndGet().toHexString
    val subscriberSeq = IndexedSeq(SubscriptionWithId(subscriptionId, subscription))
    val subRouteKeyStr = subRouteKey.getOrElse("")
    val s = SubscriptionMap(Map(subRouteKeyStr -> subscriberSeq))
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
}
