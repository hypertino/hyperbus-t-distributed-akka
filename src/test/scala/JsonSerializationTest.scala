import eu.inn.hyperbus.serialization.{JsonDecoder, JsonEncoder}
import org.scalatest.{Matchers, FreeSpec}

case class TestMsg(x: String, y: Int)

class JsonSerializationTest extends FreeSpec with Matchers {
  "JsonSerialization " - {
    "Encode" in {
      val encoder = JsonEncoder.createEncoder[TestMsg]
      val s = encoder.encode(TestMsg("yo",1))
      s should equal("""{"x":"yo","y":1}""")
    }
    "Decode" in {
      val decoder = JsonDecoder.createDecoder[TestMsg]
      val t = decoder.decode("""{"x":"yo","y":1}""")
      t should equal(TestMsg("yo",1))
    }
  }
}
