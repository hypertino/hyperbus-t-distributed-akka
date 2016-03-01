package eu.inn.hyperbus.model

import eu.inn.binders.dynamic.{Lst, Text}
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
      val query = new QueryBuilder() add ("x"→ Text("1")) add ("y" → Lst(Seq(Text("2"), Text("3")))) result()
      query.toQueryString() should equal("y=2&y=3&x=1")
    }
  }
}
