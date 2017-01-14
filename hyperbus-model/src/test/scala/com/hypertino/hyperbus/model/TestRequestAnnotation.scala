package com.hypertino.hyperbus.model

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value._
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api.uri.Uri
import org.scalatest.{FlatSpec, FreeSpec, Matchers}

@request(Method.POST, "/test-post-1/{id}")
case class TestPost1(id: String, body: TestBody1) extends Request[TestBody1]

trait TestPost1ObjectApi {
  def apply(id: String, x: TestBody1, headers: Headers): TestPost1
}

object TestPost1 extends RequestObjectApi[TestPost1] with TestPost1ObjectApi {
  def apply(id: String, x: String, headers: Headers): TestPost1 = TestPost1(id, TestBody1(x), headers)
}

@body("test-inner-body")
case class TestInnerBody(innerData: String) extends Body {
  def toEmbedded(links: Links.LinksMap = Links("/test-inner-resource")) = TestInnerBodyEmbedded(innerData, links)
}

object TestInnerBody extends BodyObjectApi[TestInnerBody]

@body("test-inner-body")
case class TestInnerBodyEmbedded(innerData: String,
                                 @fieldName("_links") links: Links.LinksMap = Links("/test-inner-resource")) extends Body with Links {

  def toOuter: TestInnerBody = TestInnerBody(innerData)
}

object TestInnerBodyEmbedded extends BodyObjectApi[TestInnerBodyEmbedded]

case class TestOuterBodyEmbedded(simple: TestInnerBodyEmbedded, collection: List[TestInnerBodyEmbedded])

@body("test-outer-body")
case class TestOuterBody(outerData: String,
                         @fieldName("_embedded") embedded: TestOuterBodyEmbedded) extends Body

object TestOuterBody extends BodyObjectApi[TestOuterBody]

@request(Method.GET, "/test-outer-resource")
case class TestOuterResource(body: TestOuterBody) extends Request[TestOuterBody]

object TestOuterResource extends RequestObjectApi[TestOuterResource]

class TestRequestAnnotation extends FlatSpec with Matchers {
  implicit val mcx = new MessagingContextFactory {
    override def newContext(): MessagingContext = new MessagingContext {
      override def correlationId: String = "123"

      override def messageId: String = "123"
    }
  }

  "TestPost1" should "serialize" in {
    val post1 = TestPost1("155", TestBody1("abcde"))
    StringSerializer.serializeToString(post1) should equal("""{"uri":{"pattern":"/test-post-1/{id}","args":{"id":"155"}},"headers":{"messageId":["123"],"method":["post"],"contentType":["test-body-1"]},"body":{"data":"abcde"}}""")
  }

  "TestPost1" should "serialize with headers" in {
    val post1 = TestPost1("155", TestBody1("abcde"), Headers("test" → Seq("a")))
    StringSerializer.serializeToString(post1) should equal("""{"uri":{"pattern":"/test-post-1/{id}","args":{"id":"155"}},"headers":{"test":["a"],"messageId":["123"],"method":["post"],"contentType":["test-body-1"]},"body":{"data":"abcde"}}""")
  }

  "TestPost1" should "deserialize" in {
    val str = """{"uri":{"pattern":"/test-post-1/{id}","args":{"id":"155"}},"headers":{"method":["post"],"contentType":["test-body-1"],"messageId":["123"]},"body":{"data":"abcde"}}"""
    val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
    val post1 = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
      requestHeader.uri should equal(Uri("/test-post-1/{id}", Map("id" → "155")))
      requestHeader.contentType should equal(Some("test-body-1"))
      requestHeader.method should equal("post")
      requestHeader.messageId should equal("123")
      requestHeader.correlationId should equal("123")
      TestPost1(requestHeader, jsonParser)
    }

    post1.body should equal(TestBody1("abcde"))
    post1.id should equal("155")
    post1.uri should equal(Uri("/test-post-1/{id}", Map(
      "id" → "155"
    )))
  }

  "TestPost1" should "deserialize from String" in {
    val str = """{"uri":{"pattern":"/test-post-1/{id}","args":{"id":"155"}},"headers":{"messageId":["123"],"method":["post"],"contentType":["test-body-1"]},"body":{"data":"abcde"}}"""
    val post1 = StringDeserializer.request[TestPost1](str)
    val post2 = TestPost1("155", TestBody1("abcde"))
    post1 should equal(post2)
  }

  "TestPost1" should "deserialize with headers" in {
    val str = """{"uri":{"pattern":"/test-post-1/{id}","args":{"id":"155"}},"headers":{"method":["post"],"contentType":["test-body-1"],"messageId":["123"],"test":["a"]},"body":{"data":"abcde"}}"""
    val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
    val post1 = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
      TestPost1(requestHeader, jsonParser)
    }

    post1.body should equal(TestBody1("abcde"))
    post1.id should equal("155")
    post1.uri should equal(Uri("/test-post-1/{id}", Map(
      "id" → "155"
    )))
    post1.headers should contain("test" → Seq("a"))
  }

  "TestOuterPost" should "serialize" in {
    val ba = new ByteArrayOutputStream()
    val inner1 = TestInnerBodyEmbedded("eklmn")
    val inner2 = TestInnerBodyEmbedded("xyz")
    val inner3 = TestInnerBodyEmbedded("yey")
    val postO = TestOuterResource(TestOuterBody("abcde",
      TestOuterBodyEmbedded(inner1, List(inner2, inner3))
    ))
    postO.serialize(ba)
    val str = ba.toString("UTF-8")
    str should equal("""{"uri":{"pattern":"/test-outer-resource"},"headers":{"messageId":["123"],"method":["get"],"contentType":["test-outer-body"]},"body":{"outerData":"abcde","_embedded":{"simple":{"innerData":"eklmn","_links":{"self":{"href":"/test-inner-resource","templated":false}}},"collection":[{"innerData":"xyz","_links":{"self":{"href":"/test-inner-resource","templated":false}}},{"innerData":"yey","_links":{"self":{"href":"/test-inner-resource","templated":false}}}]}}}""")
  }

  "TestOuterPost" should "deserialize" in {
    val str = """{"uri":{"pattern":"/test-outer-resource"},"headers":{"method":["get"],"contentType":["test-outer-body"],"messageId":["123"]},"body":{"outerData":"abcde","_embedded":{"simple":{"innerData":"eklmn","_links":{"self":{"href":"/test-inner-resource","templated":false}}},"collection":[{"innerData":"xyz","_links":{"self":{"href":"/test-inner-resource","templated":false}}},{"innerData":"yey","_links":{"self":{"href":"/test-inner-resource","templated":false}}}]}}}"""
    val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
    val outer = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
      requestHeader.uri should equal(Uri("/test-outer-resource"))
      requestHeader.contentType should equal(Some("test-outer-body"))
      requestHeader.method should equal("get")
      requestHeader.messageId should equal("123")
      requestHeader.correlationId should equal("123")
      TestOuterResource(TestOuterBody(requestHeader.contentType, jsonParser))
    }

    val inner1 = TestInnerBodyEmbedded("eklmn")
    val inner2 = TestInnerBodyEmbedded("xyz")
    val inner3 = TestInnerBodyEmbedded("yey")
    val outerBody = TestOuterBody("abcde",
      TestOuterBodyEmbedded(inner1, List(inner2, inner3))
    )

    outer.body should equal(outerBody)
    outer.uri should equal(Uri("/test-outer-resource"))
  }

  "DynamicRequest" should "decode" in {
    val str = """{"uri":{"pattern":"/test"},"headers":{"method":["custom-method"],"contentType":["test-body-1"],"messageId":["123"]},"body":{"resourceId":"100500"}}"""
    val request = DynamicRequest(str)
    request shouldBe a[Request[_]]
    request.method should equal("custom-method")
    request.uri should equal(Uri("/test"))
    request.messageId should equal("123")
    request.correlationId should equal("123")
    //request.body.contentType should equal(Some())
    request.body should equal(DynamicBody(Some("test-body-1"), ObjV("resourceId" -> "100500")))
  }

  "hashCode, equals, product" should "work" in {
    val post1 = TestPost1("155", TestBody1("abcde"))
    val post2 = TestPost1("155", TestBody1("abcde"))
    val post3 = TestPost1("155", TestBody1("abcdef"))
    post1 should equal(post2)
    post1.hashCode() should equal(post2.hashCode())
    post1 shouldNot equal(post3)
    post1.hashCode() shouldNot equal(post3.hashCode())
    post1.productElement(0) should equal("155")
    post1.productElement(1) should equal(TestBody1("abcde"))
    post1.productElement(2) shouldBe a[Map[_, _]]
  }
}

