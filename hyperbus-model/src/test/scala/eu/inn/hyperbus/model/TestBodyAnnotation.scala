package eu.inn.hyperbus.model

import eu.inn.hyperbus.model.annotations.body
import org.scalatest.{FreeSpec, Matchers}

@body("test-body-1")
case class TestBody1(data: String) extends Body

class TestBodyAnnotation extends FreeSpec with Matchers {
  "Body Annotation " - {
    "Serialize Body" in {
      import eu.inn.binders.json._
      val body = TestBody1("abcde")
      val s = body.toJson
      s should equal("""{"data":"abcde"}""")
    }

    "Deserialize Body" in {
      import eu.inn.binders.json._
      val s = """{"data":"abcde"}"""
      val body = s.parseJson[TestBody1]
      body should equal(TestBody1("abcde"))
    }
  }
}
