import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.servicebus.serialization.{JsonDecoder, JsonEncoder}
import org.scalatest.{Matchers, FreeSpec}

case class TestMsg(x: String, y: Int)

class JsonSerializationTest extends FreeSpec with Matchers {
  "JsonSerialization " - {
    "Encode" in {
      val encoder = JsonEncoder.createEncoder[TestMsg]
      val ba = new ByteArrayOutputStream()
      encoder.encode(TestMsg("yo",1),ba)
      ba.toString("UTF8") should equal("""{"x":"yo","y":1}""")
    }
    "Decode" in {
      val str = """{"x":"yo","y":1}"""
      val is = new ByteArrayInputStream(str.getBytes("UTF8"))
      val decoder = JsonDecoder.createDecoder[TestMsg]
      val t = decoder.decode(is)
      t should equal(TestMsg("yo",1))
    }
  }
}
