package eu.inn.hyperbus.model

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.binders.dynamic.{Text, Obj}
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.standard.DynamicGet
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api.{Filters, SpecificValue, Topic}
import org.scalatest.{FreeSpec, Matchers}

@request("/test-post-1/{id}")
case class TestPost1(body: TestBody1) extends Request[TestBody1] {
  override def method: String = "test-method"
}

object TestPost1 {
  def apply(x: String): TestPost1 = TestPost1(TestBody1(x))
}

@body("test-outer-body")
case class TestOuterBody(outerData: String, inner: TestInnerBody) extends Body

@body("test-inner-body")
case class TestInnerBody(innerData: String) extends Body // test _links

@request("/test-outer-post")
case class TestOuterPost(body:TestOuterBody) extends Request[TestOuterBody] {
  override def method: String = "test-method"
}

class TestRequestAnnotation extends FreeSpec with Matchers {
  "Request Annotation " - {

    "TestPost1 should serialize" in {
      val ba = new ByteArrayOutputStream()
      val post1 = TestPost1(TestBody1("155", "abcde"), messageId = "123", correlationId = "123")
      post1.serialize(ba)
      val str = ba.toString("UTF-8")
      str should equal("""{"request":{"url":"/test-post-1/{id}","method":"test-method","contentType":"test-body-1","messageId":"123"},"body":{"id":"155","data":"abcde"}}""")
    }

    "TestPost1 should deserialize" in {
      val str = """{"request":{"url":"/test-post-1/{id}","method":"test-method","contentType":"test-body-1","messageId":"123"},"body":{"id":"155","data":"abcde"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val post1 = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
        requestHeader.url should equal("/test-post-1/{id}")
        requestHeader.contentType should equal(Some("test-body-1"))
        requestHeader.method should equal("test-method")
        requestHeader.messageId should equal("123")
        requestHeader.correlationId should equal(None)
        TestPost1(TestBody1(requestHeader.contentType, jsonParser))
      }

      post1.body should equal(TestBody1("155", "abcde"))
      post1.topic should equal(Topic("/test-post-1/{id}", Filters(Map(
        "id" → SpecificValue("155")
      ))))
    }

    "TestOuterPost should serialize" in {
      val ba = new ByteArrayOutputStream()
      val postO = TestOuterPost(TestOuterBody("abcde", TestInnerBody("eklmn")), messageId = "123", correlationId = "123")
      postO.serialize(ba)
      val str = ba.toString("UTF-8")
      str should equal("""{"request":{"url":"/test-outer-post","method":"test-method","contentType":"test-outer-body","messageId":"123"},"body":{"outerData":"abcde","_embedded":{"inner":{"innerData":"eklmn"}}}}""")
    }

    /*"TestOuterPost should deserialize" in {
      val str = """{"request":{"url":"/test-post-1/{id}","method":"test-method","contentType":"test-body-1","messageId":"123"},"body":{"id":"155","data":"abcde"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val post1 = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
        requestHeader.url should equal("/test-post-1/{id}")
        requestHeader.contentType should equal(Some("test-body-1"))
        requestHeader.method should equal("test-method")
        requestHeader.messageId should equal("123")
        requestHeader.correlationId should equal(None)
        TestPost1(TestBody1(requestHeader.contentType, jsonParser))
      }

      post1.body should equal(TestBody1("155", "abcde"))
      post1.topic should equal(Topic("/test-post-1/{id}", Filters(Map(
        "id" → SpecificValue("155")
      ))))
    }*/

    "Decode DynamicGet" in {
      val str = """{"request":{"method":"get","url":"/test","messageId":"123"},"body":{"resourceId":"100500"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val request = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
        DynamicRequest(requestHeader, jsonParser)
      }
      request should equal(new DynamicGet("/test",DynamicBody(Obj(Map("resourceId" -> Text("100500")))),
        messageId = "123",
        correlationId = "123"
      ))
    }

    "Decode DynamicRequest" in {
      val str = """{"request":{"method":"custom-method","url":"/test","messageId":"123"},"body":{"resourceId":"100500"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val request = DynamicRequest(str)
      request shouldBe a [Request[_]]
      request.method should equal("custom-method")
      request.url should equal("/test")
      request.messageId should equal("123")
      request.correlationId should equal("123")
      request.body should equal(DynamicBody(Obj(Map("resourceId" -> Text("100500")))))
    }
  }
}
