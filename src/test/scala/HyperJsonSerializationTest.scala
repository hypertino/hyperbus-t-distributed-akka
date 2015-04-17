import eu.inn.binders.naming.PlainConverter
import eu.inn.hyperbus.protocol.Link
import eu.inn.hyperbus.serialization.{HyperJsonDecoder, HyperJsonEncoder}
import eu.inn.servicebus.serialization.{JsonDecoder, JsonEncoder}
import org.scalatest.{Matchers, FreeSpec}


class HyperJsonSerializationTest extends FreeSpec with Matchers {
  "HyperJsonSerialization " - {
    "Serialize Body" in {
      import eu.inn.binders.json._
      val body = TestCreatedBody("100500")
      val s = body.toJson
      s should equal("""{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true,"type":null}}}""")
    }
    "Deserialize Body" in {
      import eu.inn.binders.json._
      val s = """{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true,"type":null}}}"""
      val body = s.parseJson[TestCreatedBody]
      body should equal(TestCreatedBody("100500"))
    }

    "Encode" in {
      val encoder = HyperJsonEncoder.createEncoder[TestPost1]
      val s = encoder.encode(TestPost1(TestBody1("ha ha")))
      println(s)
      s should equal("""{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}"}""")
    }

//    "Decode" in {
//      val decoder = HyperJsonDecoder.createDecoder[TestPost1]
//      val t = decoder.decode("""{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}"}""")
//      t should equal(TestPost1(TestBody1("ha ha")))
//    }
  }
}
