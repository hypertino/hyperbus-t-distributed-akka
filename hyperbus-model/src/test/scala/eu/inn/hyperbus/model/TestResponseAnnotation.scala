package eu.inn.hyperbus.model

import java.io.ByteArrayOutputStream

import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.model.annotations.body
import org.scalatest.{FreeSpec, Matchers}

@body("test-created-body")
case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: Links.LinksMap = Map(
                             DefLink.LOCATION -> Left(Link("/resources/{resourceId}", templated = true))))
  extends CreatedBody

// with NoContentType


class TestResponseAnnotation extends FreeSpec with Matchers {
  "Response Annotation " - {
    implicit val mcx = new MessagingContextFactory {
      override def newContext(): MessagingContext = new MessagingContext {
        override def correlationId: String = "abc"

        override def messageId: String = "123"
      }
    }

    "Serialize Response" in {
      val msg = Created(TestCreatedBody("100500"))
      val ba = new ByteArrayOutputStream()
      msg.serialize(ba)
      val s = ba.toString("UTF8")
      //println(s)
      s should equal("""{"response":{"status":201,"headers":{"messageId":["123"],"correlationId":["abc"],"contentType":["test-created-body"]}},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}""")
    }

    "Serialize Response with headers" in {
      val msg = Created(TestCreatedBody("100500"), Headers("test" → Seq("a")))
      val ba = new ByteArrayOutputStream()
      msg.serialize(ba)
      val s = ba.toString("UTF8")
      //println(s)
      s should equal("""{"response":{"status":201,"headers":{"test":["a"],"messageId":["123"],"correlationId":["abc"],"contentType":["test-created-body"]}},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}""")
    }

    "hashCode, equals, product" in {
      val r1 =  Created(TestCreatedBody("100500"))
      val r2 =  Created(TestCreatedBody("100500"))
      val r3 =  Created(TestCreatedBody("1005001"))
      r1 should equal(r2)
      r1.hashCode() should equal(r2.hashCode())
      r1 shouldNot equal(r3)
      r1.hashCode() shouldNot equal(r3.hashCode())
      r1.productElement(0) shouldBe a[TestCreatedBody]
      r1.productElement(1) shouldBe a[Map[_,_]]

      val o: Any = r1
      o match {
        case Created(body,headers) ⇒
          body shouldBe a[TestCreatedBody]
          headers shouldBe a[Map[_,_]]
        case _ ⇒ fail("unapply didn't matched for a response")
      }
    }
  }
}
