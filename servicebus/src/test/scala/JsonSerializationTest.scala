import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.scalatest.{Matchers, FreeSpec}

case class TestMsg(x: String, y: Int)

class JsonSerializationTest extends FreeSpec with Matchers {
  "JsonSerialization " - {
    "Encode" in {
      val encoder = eu.inn.servicebus.serialization.createEncoder[TestMsg]
      val ba = new ByteArrayOutputStream()
      encoder(TestMsg("yo",1),ba)
      ba.toString("UTF8") should equal("""{"x":"yo","y":1}""")
    }
    "Decode" in {
      val str = """{"x":"yo","y":1}"""
      val is = new ByteArrayInputStream(str.getBytes("UTF8"))
      val decoder = eu.inn.servicebus.serialization.createDecoder[TestMsg]
      val t = decoder(is)
      t should equal(TestMsg("yo",1))
    }
  }
}
