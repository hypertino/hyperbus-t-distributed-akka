package eu.inn.hyperbus.model.utils

import eu.inn.binders.value.{Lst, LstV, Text}
import eu.inn.hyperbus.model.{QueryBody, QueryBuilder}
import org.scalatest.{FreeSpec, Matchers}

class SortSpec extends FreeSpec with Matchers {
  "Sort" - {
    "should decode" in {
      Sort.parse("field1") should equal(Seq(SortBy("field1")))
      Sort.parse("-field1") should equal(Seq(SortBy("field1", descending = true)))
      Sort.parse("field1,field2") should equal(Seq(SortBy("field1"),SortBy("field2")))
      Sort.parse("-field1,+field2") should equal(Seq(SortBy("field1", descending = true),SortBy("field2")))
      Sort.parse("") should equal(Seq.empty)
      Sort.parse("   ") should equal(Seq.empty)
      Sort.parse(" -field1  , +field2") should equal(Seq(SortBy("field1", descending = true),SortBy("field2")))
    }

    "should decode implicit from QueryBody" in {
      val q = QueryBody.fromQueryString("?sort=-field1%2C%2Bfield2%2Cfield3")
      import Sort._

      q.sortBy.map(_.toList) should equal(Some(List(SortBy("field1", descending = true),SortBy("field2"),SortBy("field3"))))
    }

    "should encode to" in {
      Sort.generate(Seq(SortBy("field1"))) should equal("field1")
      Sort.generate(Seq(SortBy("field1"),SortBy("field2",descending = true))) should equal("field1,-field2")
    }

    "should encode implicitly to QueryBuilder" in {
      val q = new QueryBuilder()
      import Sort._
      q.sortBy(Seq(SortBy("field1"),SortBy("field2",descending = true)))
      q.result().toQueryString() should equal("sort=field1%2C-field2")
    }
  }
}
