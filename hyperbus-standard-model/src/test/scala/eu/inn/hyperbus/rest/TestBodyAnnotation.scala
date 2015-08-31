package eu.inn.hyperbus.rest

import eu.inn.hyperbus.rest.annotations.body
import org.scalatest.{FreeSpec, Matchers}

@body("test-body-1")
case class TestBody1(id: String, data: String) extends Body

object TestBody1 {
  def apply(x: String): TestBody1 = TestBody1(x, "no-data")
}

class TestBodyAnnotation extends FreeSpec with Matchers {
  "Body Annotation " - {
    "Serialize Body" in {
      import eu.inn.binders.json._
      val body = TestBody1("100500", "abcde")
      val s = body.toJson
      s should equal("""{"id":"100500","data":"abcde"}""")
    }

    "Deserialize Body" in {
      import eu.inn.binders.json._
      val s = """{"id":"100500","data":"abcde"}"""
      val body = s.parseJson[TestBody1]
      body should equal(TestBody1("100500", "abcde"))
    }
  }
}
