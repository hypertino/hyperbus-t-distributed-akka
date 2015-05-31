package eu.inn.servicebus.util

import scala.collection.concurrent.TrieMap

trait ComplexElement[ME, REMOVE] {
  def upsert(upsertPart: ME): ME

  def remove(removePart: REMOVE): ME

  def isEmpty: Boolean
}

class ComplexTrieMap[K, REMOVE, V <: ComplexElement[V, REMOVE]] {
  protected val map = new TrieMap[K, V]

  def get(key: K): Option[V] = map.get(key)

  def getOrElse(key: K, default: => V): V = map.getOrElse(key, default)

  def upsert(key: K, value: V): Unit = {
    this.synchronized {
      map.putIfAbsent(key, value).map { existing =>
        val n = existing.upsert(value)
        val x = map.put(key, n)
        x
      }
    }
  }

  def remove(key: K, value: REMOVE): Unit = {
    this.synchronized {
      map.get(key).map { existing =>
        val nv = existing.remove(value)
        if (nv.isEmpty)
          map.remove(key)
        else
          map.put(key, nv)
      }
    }
  }
}
