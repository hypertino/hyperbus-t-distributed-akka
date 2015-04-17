import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.binders.naming.PlainConverter
import eu.inn.hyperbus.protocol.{Created, Link}
import eu.inn.hyperbus.serialization.{HyperJsonDecoder, HyperJsonEncoder}
import eu.inn.servicebus.serialization.{JsonDecoder, JsonEncoder}
import org.scalatest.{Matchers, FreeSpec}


class HyperJsonSerializationTest extends FreeSpec with Matchers {
  "HyperJsonSerialization " - {
    "Serialize Body" in {
      import eu.inn.binders.json._
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
      val encoder = HyperJsonEncoder.createEncoder[TestPost1]
      val ba = new ByteArrayOutputStream()
      encoder.encode(TestPost1(TestBody1("ha ha")),ba)
      val s = ba.toString("UTF8")
      //println(s)
      s should equal("""{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}""")
    }

    "Encode Response" in {
      val encoder = HyperJsonEncoder.createEncoder[Created[TestCreatedBody]]
      val ba = new ByteArrayOutputStream()
      encoder.encode(new Created(TestCreatedBody("100500")),ba)
      val s = ba.toString("UTF8")
      //println(s)
      s should equal("""{"response":{"status":201},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}""")
    }

    "Decode Response" in {
      val decoder = HyperJsonDecoder.createDecoder[Created[TestCreatedBody]]
      val s = """{"response":{"status":201},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true,"type":null}}}}"""
      val is = new ByteArrayInputStream(s.getBytes("UTF8"))
      val r = decoder.decode(is)
      //println(s)
      r should equal(new Created(TestCreatedBody("100500")))
    }

//    "Decode" in {
//      val decoder = HyperJsonDecoder.createDecoder[TestPost1]
//      val t = decoder.decode("""{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}"}""")
//      t should equal(TestPost1(TestBody1("ha ha")))
//    }
  }
}
