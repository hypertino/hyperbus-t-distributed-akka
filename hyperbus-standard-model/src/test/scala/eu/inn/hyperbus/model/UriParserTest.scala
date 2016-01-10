package eu.inn.hyperbus.model

import org.scalatest.{FreeSpec, Matchers}

class UriParserTest extends FreeSpec with Matchers {
  "UrlParserTest " - {
    "Parse Url" in {
      val p: String ⇒ Seq[String] = UriParser.extractParameters
      p("{abc}") should equal(Seq("abc"))
      p("/{abc}/") should equal(Seq("abc"))
      p("x/{abc}/y") should equal(Seq("abc"))
      p("x/{abc}/y/{def}") should equal(Seq("abc", "def"))
      p("{abc}{def}") should equal(Seq("abc", "def"))
    }
  }
}
