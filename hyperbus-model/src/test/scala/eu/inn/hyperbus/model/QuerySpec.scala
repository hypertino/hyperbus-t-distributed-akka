package eu.inn.hyperbus.model

import eu.inn.binders.dynamic.{LstV, Lst, Text}
import org.scalatest.{Matchers, FreeSpec}

class QuerySpec extends FreeSpec with Matchers {
  "Query" - {
    "Should decode from QueryString" in {
      Query.fromQueryString("?x=1&y=2&y=3") should equal(
        new QueryBuilder() add ("x"→ Text("1")) add ("y" → Lst(Seq(Text("2"), Text("3")))) result()
      )

      Query.fromQueryString("?x=1&y=2&y=3&y=4") should equal(
        new QueryBuilder() add ("x"→ Text("1")) add ("y" → Lst(Seq(Text("2"), Text("3"), Text("4")))) result()
      )

      Query.fromQueryString("x=1&y=2") should equal(
        new QueryBuilder() add ("x"→ Text("1")) add ("y" → Text("2")) result()
      )
    }

    "Should encode to" in {
      val query = new QueryBuilder() add ("x"→ "1") add ("y" → LstV("2", "3")) result()
      query.toQueryString() should equal("y=2&y=3&x=1")
    }

    "sortBy should decode" in {
      Query.fromQueryString("?sort.by=field1").sortBy should equal(Seq(SortBy("field1")))
      Query.fromQueryString("?sort.by=field1%3Adesc").sortBy should equal(Seq(SortBy("field1", true)))
      Query.fromQueryString("?sort.by=field1%2Cfield2").sortBy should equal(Seq(SortBy("field1"),SortBy("field2")))
      Query.fromQueryString("?sort.by=field1%3Adesc%2Cfield2%3Aasc").sortBy should equal(Seq(SortBy("field1", true),SortBy("field2")))
    }

    "sort.by should encode to" in {
      new QueryBuilder() sortBy("field1") result() toQueryString() should equal("sort.by=field1")
      new QueryBuilder() sortBy("field1") sortBy("field2",true) result() toQueryString() should equal("sort.by=field1%2Cfield2%3Adesc")
    }
  }
}
