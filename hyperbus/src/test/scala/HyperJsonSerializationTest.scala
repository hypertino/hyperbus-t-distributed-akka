import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.binders.dynamic.{Text, Obj}
import eu.inn.hyperbus.protocol.{DynamicBody, DynamicGet, Created}
import eu.inn.hyperbus.serialization.impl.Helpers
import org.scalatest.{Matchers, FreeSpec}


class HyperJsonSerializationTest extends FreeSpec with Matchers {
  "HyperJsonSerialization " - {
    "Serialize Body" in {
      import eu.inn.binders.json._
      import Helpers.bindOptions // by default skip null fields
      val body = TestCreatedBody("100500")
      val s = body.toJson
      s should equal("""{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}""")
    }

    "Deserialize Body" in {
      import eu.inn.binders.json._
      val s = """{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}"""
      val body = s.parseJson[TestCreatedBody]
      body should equal(TestCreatedBody("100500"))
    }

    "Encode Request" in {
      val encoder = eu.inn.hyperbus.serialization.createEncoder[TestPost1]
      val ba = new ByteArrayOutputStream()
      encoder(TestPost1(TestBody1("ha ha")),ba)
      val s = ba.toString("UTF8")
      //println(s)
      s should equal("""{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}""")
    }

    "Encode Response" in {
      val encoder = eu.inn.hyperbus.serialization.createEncoder[Created[TestCreatedBody]]
      val ba = new ByteArrayOutputStream()
      encoder(new Created(TestCreatedBody("100500")),ba)
      val s = ba.toString("UTF8")
      //println(s)
      s should equal("""{"response":{"status":201,"contentType":"application/vnd+created-body.json"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}""")
    }
/*
    "Decode DynamicRequest" in {
      val s = """{"request":{"method":"get","url":"/test"},"body":{"resourceId":"100500"}}"""
      val is = new ByteArrayInputStream(s.getBytes("UTF8"))
      val d = Helpers.decodeRequestWith(is) { (rh, is2) =>
        Helpers.decodeDynamicRequest(rh, is2)
      }

      d should equal(new DynamicGet("/test",DynamicBody(Obj(Map("resourceId" -> Text("100500"))))))
    }*/
  }
}
