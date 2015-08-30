package eu.inn.hyperbus.rest

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.fasterxml.jackson.core.JsonParser
import eu.inn.hyperbus.rest.annotations.{request, body}
import eu.inn.hyperbus.serialization._
import org.scalatest.{FreeSpec, Matchers}


@body("test-body-1")
case class TestBody1(id: String, data: String) extends Body

object TestBody1 {
  def apply(x: String): TestBody1 = TestBody1(x, "no-data")
}

@request("/test-post-1/{id}")
case class TestPost1(body: TestBody1) extends Request[TestBody1] {
  override def method: String = "test-method"
}

object TestPost1 {
  def apply(x: String): TestPost1 = TestPost1(TestBody1(x))
}

class TestRequestAnnotation extends FreeSpec with Matchers {
  "Request Annotation " - {
    "TestPost1 should serialize" in {
      val ba = new ByteArrayOutputStream()
      val post1 = TestPost1(TestBody1("1", "abcde"), messageId = "123", correlationId = "123")
      post1.encode(ba)
      val str = ba.toString("UTF-8")
      str should equal("""{"request":{"url":"/test-post-1/{id}","method":"test-method","contentType":"test-body-1","messageId":"123"},"body":{"id":"1","data":"abcde"}}""")
    }
    "TestPost1 should deserialize" in {
      val str = """{"request":{"url":"/test-post-1/{id}","method":"test-method","contentType":"test-body-1","messageId":"123"},"body":{"id":"1","data":"abcde"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      MessageDecoder.decodeRequestWith(bi) { (requestHeader, jsonParser) ⇒
        requestHeader.url should equal("/test-post-1/{id}")
        requestHeader.contentType should equal(Some("test-body-1"))
        requestHeader.method should equal("test-method")
        requestHeader.messageId should equal("123")
        requestHeader.correlationId should equal(None)

        val testbody1 = TestBody1(jsonParser)
        testbody1 should equal(TestBody1("1", "abcde"))
        TestPost1(testbody1)
      }
    }
    // todo: + test for response
  }
}
