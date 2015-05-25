package eu.inn.servicebus.util

import scala.collection.concurrent.TrieMap

trait ComplexElement[ME] extends AnyVal {
  def upsert(upsertPart: ME): ME
  def remove(removePart: ME): ME
  def isEmpty: Boolean
}

class ComplexTrieMap[K, V <: ComplexElement[V]] {
  protected val map = new TrieMap[K,V]

  def get(key: K): Option[V] = map.get(key)
  def upsert(key: K, value: V): V = {
    _applyOn(key, value, _.upsert(value)).get
  }

  def remove(key: K, value: V): Unit = {
    _applyOn(key, value, _.remove(value))
  }

  protected def _applyOn(key: K, value: V, f: Function1[V,V]): Option[V] = {
    this.synchronized {
      map.putIfAbsent(key, value).flatMap { existing =>
        val n = f(existing)
        if (n.isEmpty)
          map.remove(key)
        else
          map.put(key, n)
      }
    }
  }
}
