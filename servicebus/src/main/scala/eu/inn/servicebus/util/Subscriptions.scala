package eu.inn.servicebus.util

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.transport.{AnyValue, PartitionArg}

import scala.collection.concurrent.TrieMap

case class SubscriptionWithId[T](subscriptionId:String, subscription:T)

case class SubscriptionKey(groupName: Option[String], partitionArgs: Map[String,PartitionArg]) {

  def matchArgs(args: Map[String, PartitionArg]): Boolean = {
    partitionArgs.map { case (k, v) ⇒
      args.get(k).map { av ⇒
        av.matchArg(v)
      } getOrElse {
        v == AnyValue
      }
    }.forall(r => r)
  }
}

class Subscriptions[T] {

  case class SubscriptionMap(subRoutes: Map[SubscriptionKey,IndexedSeq[SubscriptionWithId[T]]]) extends ComplexElement[SubscriptionMap, String] {
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
  protected val idCounter = new AtomicLong(0)

  def get(routeKey: String) : SubscriptionMap = routes.getOrElse(routeKey, SubscriptionMap(Map()))
  def getRouteKeyById(subscriptionId:String) = routeKeyById.get(subscriptionId)

  def add(routeKey: String, subRouteKey: SubscriptionKey, subscription:T): String = {
    val subscriptionId = idCounter.incrementAndGet().toHexString
    val subscriberSeq = IndexedSeq(SubscriptionWithId(subscriptionId, subscription))
    val s = SubscriptionMap(Map(subRouteKey -> subscriberSeq))
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
