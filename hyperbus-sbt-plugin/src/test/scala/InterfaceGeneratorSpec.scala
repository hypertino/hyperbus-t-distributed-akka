import java.util

import com.mulesoft.raml.webpack.holders.JSConsole
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.mulesoft.raml1.java.parser.path.resolver.IJavaPathResolver
import eu.inn.hyperbus.raml.utils.JsToLogConsole
import eu.inn.hyperbus.raml.{GeneratorOptions, InterfaceGenerator}
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.JavaConversions
import scala.io.Source

class InterfaceGeneratorSpec extends FreeSpec with Matchers {
  val referenceValue = s"""
    object BookTag {
      type StringEnum = String
      val NEW = "new"
      val BEST_SELLER = "best-seller"
      val CLASSICS = "classics"
      lazy val values = Seq(NEW,BEST_SELLER,CLASSICS)
      lazy val valuesSet = values.toSet
    }

    case class BookProperties(
        publishYear: Long,
        issn: String,
        tag: BookTag.StringEnum
      )

    @body("book")
    case class Book(
        bookId: String,
        authorId: String,
        bookName: String,
        authorName: String,
        bookProperties: BookProperties,
        @fieldName("_links") links: Links.LinksMap = Book.defaultLinks
      ) extends Body with Links

    object Book{
      val selfPattern = "/authors/{authorId}/books/{bookId}"
      val defaultLinks = Links(selfPattern, templated = true)
    }

    @body("book-transaction")
    case class BookTransaction(
        transactionId: String
      ) extends Body

    @body("book-created-transaction")
    case class BookCreatedTransaction(
        transactionId: String,
        @fieldName("_links") links: Links.LinksMap
      ) extends Body with Links with CreatedBody

    @body("click")
    case class Click(
        clickUrl: String
      ) extends Body

    @body("click-confirmation")
    case class ClickConfirmation(
        id: String,
        @fieldName("_links") links: Links.LinksMap
      ) extends Body with Links with CreatedBody

    @body("clicks-information")
    case class ClicksInformation(
        count: Long,
        lastRegistered: Option[java.util.Date],
        firstInserted: Option[java.util.Date]
      ) extends Body


    // --------------------

    @request(Method.GET, "/authors/{authorId}/books/{bookId}")
    case class AuthorBookGet(
        authorId: String,
        bookId: String,
        body: QueryBody
      ) extends Request[QueryBody]
      with DefinedResponse[
        Ok[Book]
      ]

    @request(Method.PUT, "/authors/{authorId}/books/{bookId}")
    case class AuthorBookPut(
        authorId: String,
        bookId: String,
        body: Book
      ) extends Request[Book]
      with DefinedResponse[(
        Ok[DynamicBody],
        Created[DynamicBody with CreatedBody]
      )]

    @request(Method.GET, "/authors/{authorId}/books")
    case class AuthorBooksGet(
        authorId: String,
        body: QueryBody
      ) extends Request[QueryBody]
      with DefinedResponse[
        Ok[DynamicBody]
      ]

    @request(Method.POST, "/authors/{authorId}/books")
    case class AuthorBooksPost(
        authorId: String,
        body: DynamicBody
      ) extends Request[DynamicBody]
      with DefinedResponse[(
        Ok[BookTransaction],
        Created[BookCreatedTransaction]
      )]

    @request(Method.POST, "/clicks")
    case class ClicksPost(
        body: Click
      ) extends Request[Click]
      with DefinedResponse[
        Created[ClickConfirmation]
      ]

    @request(Method.GET, "/clicks/{clickUrl}")
    case class ClickGet(
        clickUrl: String,
        body: QueryBody
      ) extends Request[QueryBody]
  """

  def normalize(s: String): String = {
    s.foldLeft (("",true)) { case ((r: String, prevIsSpace: Boolean), c: Char) ⇒
      val c2 = c match {
        case '\r' ⇒ ' '
        case '\t' ⇒ ' '
        case _ ⇒ c
      }
      if (c2 == ' ' && prevIsSpace) {
        (r, prevIsSpace)
      }
      else {
        (r + c2, c2 == ' ' || c2 == '\n')
      }
    }._1
  }

  "RAML" in {
    import JavaConversions._
    val factory = new JavaNodeFactory()

    val existingConsole = factory.getBindings.get("console").asInstanceOf[JSConsole]
    factory.getBindings.put("console", new JsToLogConsole(existingConsole.engine))

    factory.setPathResolver(new IJavaPathResolver {
      override def list(path: String): util.List[String] = List("test.raml")
      override def content(path: String): String = {
        val resource = getClass.getResource(path)
        if (resource == null) {
          throw new IllegalArgumentException(s"resource not found: $path")
        }
        val source = Source.fromURL(resource)
        source.getLines().mkString("\n")
      }
    })

    val api = factory.createApi("test.raml")
    api.getErrors.foreach(s ⇒ println(s"---> $s"))

    val gen = new InterfaceGenerator(api, GeneratorOptions(packageName = "eu.inn.raml"))
    val result = gen.generate()

    normalize(result) should include(normalize(referenceValue))
    result should include("package eu.inn.raml")
  }
}
