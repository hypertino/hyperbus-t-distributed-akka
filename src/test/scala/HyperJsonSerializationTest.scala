import eu.inn.binders.naming.PlainConverter
import eu.inn.hyperbus.protocol.Link
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

    /*"Encode" in {
      val encoder = JsonEncoder.createEncoder[TestMsg]
      val s = encoder.encode(TestMsg("yo",1))
      s should equal("""{"x":"yo","y":1}""")
    }
    "Decode" in {
      val decoder = JsonDecoder.createDecoder[TestMsg]
      val t = decoder.decode("""{"x":"yo","y":1}""")
      t should equal(TestMsg("yo",1))
    }*/
  }
}
