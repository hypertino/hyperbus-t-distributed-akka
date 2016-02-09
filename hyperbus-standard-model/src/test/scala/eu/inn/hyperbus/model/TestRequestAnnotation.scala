package eu.inn.hyperbus.model

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.{Text, Obj}
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.standard.{DefLink, StaticGet, DynamicGet}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.uri.{UriParts, Uri}
import org.scalatest.{FreeSpec, Matchers}

@request("/test-post-1/{id}")
case class TestPost1(id: String, body: TestBody1) extends Request[TestBody1] {
  override def method: String = "test-method"
}

object TestPost1 {
  def apply(id: String, x: String): TestPost1 = TestPost1(id, TestBody1(x))
}

@body("test-inner-body")
case class TestInnerBody(innerData: String) extends Body {

  def toEmbedded(links: LinksMap.LinksMapType = LinksMap("/test-inner-resource")) = TestInnerBodyEmbedded(innerData, links)
}

@body("test-inner-body")
case class TestInnerBodyEmbedded(innerData: String,
                                 @fieldName("_links") links: LinksMap.LinksMapType = LinksMap("/test-inner-resource")) extends Body with Links {

  def toOuter: TestInnerBody = TestInnerBody(innerData)
}

case class TestOuterBodyEmbedded(simple: TestInnerBodyEmbedded, collection: List[TestInnerBodyEmbedded])

@body("test-outer-body")
case class TestOuterBody(outerData: String,
                         @fieldName("_embedded") embedded: TestOuterBodyEmbedded) extends Body

@request("/test-outer-resource")
case class TestOuterResource(body:TestOuterBody) extends StaticGet(body)

class TestRequestAnnotation extends FreeSpec with Matchers {
  "Request Annotation " - {

    "TestPost1 should serialize" in {
      val post1 = TestPost1("155", TestBody1("abcde"), headers=Map.empty, messageId = "123", correlationId = "123")
      post1.serializeToString() should equal("""{"request":{"uri":{"pattern":"/test-post-1/{id}","args":{"id":"155"}},"method":"test-method","contentType":"test-body-1","messageId":"123"},"body":{"data":"abcde"}}""")
    }

    "TestPost1 should serialize with headers" in {
      val post1 = TestPost1("155", TestBody1("abcde"), headers=Map("test" → Seq("a")), messageId = "123", correlationId = "123")
      post1.serializeToString() should equal("""{"request":{"uri":{"pattern":"/test-post-1/{id}","args":{"id":"155"}},"method":"test-method","contentType":"test-body-1","messageId":"123","headers":{"test":["a"]}},"body":{"data":"abcde"}}""")
    }

    "TestPost1 should deserialize" in {
      val str = """{"request":{"uri":{"pattern":"/test-post-1/{id}","args":{"id":"155"}},"method":"test-method","contentType":"test-body-1","messageId":"123"},"body":{"data":"abcde"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val post1 = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
        requestHeader.uri should equal(Uri("/test-post-1/{id}", Map("id" → "155")))
        requestHeader.contentType should equal(Some("test-body-1"))
        requestHeader.method should equal("test-method")
        requestHeader.messageId should equal("123")
        requestHeader.correlationId should equal(None)
        TestPost1(requestHeader, jsonParser)
      }

      post1.body should equal(TestBody1("abcde"))
      post1.id should equal("155")
      post1.uri should equal(Uri("/test-post-1/{id}", Map(
        "id" → "155"
      )))
    }

    "TestPost1 should deserialize with headers" in {
      val str = """{"request":{"uri":{"pattern":"/test-post-1/{id}","args":{"id":"155"}},"method":"test-method","contentType":"test-body-1","messageId":"123","headers":{"test":["a"]}},"body":{"data":"abcde"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val post1 = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
        TestPost1(requestHeader, jsonParser)
      }

      post1.body should equal(TestBody1("abcde"))
      post1.id should equal("155")
      post1.uri should equal(Uri("/test-post-1/{id}", Map(
        "id" → "155"
      )))
      post1.headers should equal(Map("test" → Seq("a")))
    }

    "TestOuterPost should serialize" in {
      val ba = new ByteArrayOutputStream()
      val inner1 = TestInnerBodyEmbedded("eklmn")
      val inner2 = TestInnerBodyEmbedded("xyz")
      val inner3 = TestInnerBodyEmbedded("yey")
      val postO = TestOuterResource(TestOuterBody("abcde",
        TestOuterBodyEmbedded(inner1, List(inner2,inner3))
      ), headers=Map.empty, messageId = "123", correlationId = "123")
      postO.serialize(ba)
      val str = ba.toString("UTF-8")
      str should equal("""{"request":{"uri":{"pattern":"/test-outer-resource"},"method":"get","contentType":"test-outer-body","messageId":"123"},"body":{"outerData":"abcde","_embedded":{"simple":{"innerData":"eklmn","_links":{"self":{"href":"/test-inner-resource","templated":true}}},"collection":[{"innerData":"xyz","_links":{"self":{"href":"/test-inner-resource","templated":true}}},{"innerData":"yey","_links":{"self":{"href":"/test-inner-resource","templated":true}}}]}}}""")
    }

    "TestOuterPost should deserialize" in {
      val str = """{"request":{"uri":{"pattern":"/test-outer-resource"},"method":"get","contentType":"test-outer-body","messageId":"123"},"body":{"outerData":"abcde","_embedded":{"simple":{"innerData":"eklmn","_links":{"self":{"href":"/test-inner-resource","templated":true}}},"collection":[{"innerData":"xyz","_links":{"self":{"href":"/test-inner-resource","templated":true}}},{"innerData":"yey","_links":{"self":{"href":"/test-inner-resource","templated":true}}}]}}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val outer = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
        requestHeader.uri should equal(Uri("/test-outer-resource"))
        requestHeader.contentType should equal(Some("test-outer-body"))
        requestHeader.method should equal("get")
        requestHeader.messageId should equal("123")
        requestHeader.correlationId should equal(None)
        TestOuterResource(TestOuterBody(requestHeader.contentType, jsonParser))
      }

      val inner1 = TestInnerBodyEmbedded("eklmn")
      val inner2 = TestInnerBodyEmbedded("xyz")
      val inner3 = TestInnerBodyEmbedded("yey")
      val outerBody = TestOuterBody("abcde",
        TestOuterBodyEmbedded(inner1, List(inner2,inner3))
      )

      outer.body should equal(outerBody)
      outer.uri should equal(Uri("/test-outer-resource"))
    }

    "Decode DynamicGet" in {
      val str = """{"request":{"method":"get","uri":{"pattern":"/test"},"messageId":"123"},"body":{"resourceId":"100500"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val request = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
        DynamicRequest(requestHeader, jsonParser)
      }
      request should equal(new DynamicGet(Uri("/test"),DynamicBody(Obj(Map("resourceId" -> Text("100500")))),
        headers=Map.empty,
        messageId = "123",
        correlationId = "123"
      ))
    }

    "Decode DynamicRequest" in {
      val str = """{"request":{"method":"custom-method","uri":{"pattern":"/test"},"contentType":"test-body-1","messageId":"123"},"body":{"resourceId":"100500"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val request = DynamicRequest(str)
      request shouldBe a [Request[_]]
      request.method should equal("custom-method")
      request.uri should equal(Uri("/test"))
      request.messageId should equal("123")
      request.correlationId should equal("123")
      //request.body.contentType should equal(Some())
      request.body should equal(DynamicBody(Some("test-body-1"), Obj(Map("resourceId" -> Text("100500")))))
    }
  }
}
