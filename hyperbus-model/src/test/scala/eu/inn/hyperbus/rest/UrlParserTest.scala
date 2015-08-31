package eu.inn.hyperbus.rest

import org.scalatest.{FreeSpec, Matchers}

class UrlParserTest extends FreeSpec with Matchers {
  "UrlParserTest " - {
    "Parse Url" in {
      val p: String ⇒ Seq[String] = UrlParser.extractParameters
      p("{abc}") should equal(Seq("abc"))
      p("/{abc}/") should equal(Seq("abc"))
      p("x/{abc}/y") should equal(Seq("abc"))
      p("x/{abc}/y/{def}") should equal(Seq("abc", "def"))
      p("{abc}{def}") should equal(Seq("abc", "def"))
    }
  }
}
