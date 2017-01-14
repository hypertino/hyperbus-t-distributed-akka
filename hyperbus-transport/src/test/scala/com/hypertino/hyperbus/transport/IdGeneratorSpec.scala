package com.hypertino.hyperbus.transport

import com.hypertino.hyperbus.IdGenerator
import org.scalatest.{Matchers, FreeSpec}

class IdGeneratorSpec extends FreeSpec with Matchers {
  "IdGenerator " - {
    "Should generate sorted sequence" in {

      val list = 0 until 5000 map { i ⇒ IdGenerator.create() }
      list.sortBy(l ⇒ l) should equal(list) // sequence is sorted
      list.foreach { l ⇒
        list.count(_ == l) should equal(1) //evey one is unique
        l.length == 30
      }
    }
  }
}
