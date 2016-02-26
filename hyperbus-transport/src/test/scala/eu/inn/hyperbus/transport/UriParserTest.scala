package eu.inn.hyperbus.transport

import eu.inn.hyperbus.transport.api.uri._
import org.scalatest.{FreeSpec, Matchers}

class UriParserTest extends FreeSpec with Matchers {
  "UriParserTest " - {
    "Extract parameters" in {
      val p: String ⇒ Seq[String] = UriParser.extractParameters
      p("{abc}") should equal(Seq("abc"))
      p("/{abc}/") should equal(Seq("abc"))
      p("x/{abc}/y") should equal(Seq("abc"))
      p("x/{abc}/y/{def}") should equal(Seq("abc", "def"))
      p("{abc}{def}") should equal(Seq("abc", "def"))
    }

    "Parse URI" in {
      val p: String ⇒ Seq[Token] = UriParser.tokens
      p("{abc}") should equal(Seq(ParameterToken("abc")))
      p("/{abc}/") should equal(Seq(SlashToken, ParameterToken("abc"), SlashToken))
      p("x/{abc}/y") should equal(Seq(
        TextToken("x"), SlashToken, ParameterToken("abc"), SlashToken, TextToken("y"))
      )
      p("x/{abc}/y/{def}") should equal(Seq(
        TextToken("x"), SlashToken, ParameterToken("abc"),
        SlashToken, TextToken("y"), SlashToken, ParameterToken("def"))
      )
      p("{abc}{def}") should equal(Seq(ParameterToken("abc"), ParameterToken("def")))
      p("abcdef") should equal(Seq(TextToken("abcdef")))
      p("abc/def") should equal(Seq(TextToken("abc"), SlashToken, TextToken("def")))
      p("{abc:@}") should equal(Seq(ParameterToken("abc", RegularMatchType)))
      p("{abc:*}") should equal(Seq(ParameterToken("abc", PathMatchType)))
    }

    "Format URI" in {
      val uri = Uri("x/{abc}/y/{def}", Map("abc" → "123", "def" → "456"))
      uri.formatted should equal("x/123/y/456")
    }
  }
}
