import eu.inn.hyperbus.raml.{GeneratorOptions, InterfaceGenerator}
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch.{Diff, Operation}
import org.raml.v2.api.RamlModelBuilder
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
        publishYear: Short,
        sold: Int,
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

    object Book extends BodyObjectApi[Book] {
      val selfPattern = "/authors/{authorId}/books/{bookId}"
      val defaultLinks = Links(selfPattern, templated = true)
    }

    @body("book-transaction")
    case class BookTransaction(
        transactionId: String
      ) extends Body

    object BookTransaction extends BodyObjectApi[BookTransaction]

    @body("book-created-transaction")
    case class BookCreatedTransaction(
        transactionId: String,
        @fieldName("_links") links: Links.LinksMap
      ) extends Body with Links with CreatedBody

    object BookCreatedTransaction extends BodyObjectApi[BookCreatedTransaction]

    @body("click")
    case class Click(
        clickUrl: String
      ) extends Body

    object Click extends BodyObjectApi[Click]

    @body("click-confirmation")
    case class ClickConfirmation(
        id: String,
        @fieldName("_links") links: Links.LinksMap
      ) extends Body with Links with CreatedBody

    object ClickConfirmation extends BodyObjectApi[ClickConfirmation]

    @body("clicks-information")
    case class ClicksInformation(
        count: Long,
        lastRegistered: Option[java.util.Date],
        firstInserted: Option[java.util.Date]
      ) extends Body

    object ClicksInformation extends BodyObjectApi[ClicksInformation]

    case class Author(
      name: String,
      books: Seq[Book]
    )


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

    object AuthorBookGet extends RequestObjectApi[AuthorBookGet]

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

    object AuthorBookPut extends RequestObjectApi[AuthorBookPut]

    @request(Method.GET, "/authors/{authorId}/books")
    case class AuthorBooksGet(
        authorId: String,
        body: QueryBody
      ) extends Request[QueryBody]
      with DefinedResponse[
        Ok[DynamicBody]
      ]

    object AuthorBooksGet extends RequestObjectApi[AuthorBooksGet]

    @request(Method.POST, "/authors/{authorId}/books")
    case class AuthorBooksPost(
        authorId: String,
        body: DynamicBody
      ) extends Request[DynamicBody]
      with DefinedResponse[(
        Ok[BookTransaction],
        Created[BookCreatedTransaction]
      )]

    object AuthorBooksPost extends RequestObjectApi[AuthorBooksPost]

    @request(Method.POST, "/clicks")
    case class ClicksPost(
        body: Click
      ) extends Request[Click]
      with DefinedResponse[
        Created[ClickConfirmation]
      ]

    object ClicksPost extends RequestObjectApi[ClicksPost]

    @request(Method.GET, "/clicks/{clickUrl}")
    case class ClickGet(
        clickUrl: String,
        body: QueryBody
      ) extends Request[QueryBody]

    object ClickGet extends RequestObjectApi[ClickGet]
  """

  def normalize(s: String): String = {
    val step1 = s.foldLeft (("",true)) { case ((r: String, prevIsSpace: Boolean), c: Char) ⇒
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
    step1.replace("  ", " ").trim()
  }

  "RAML" in {
    import JavaConversions._

    val path = "test.raml"
    val resource = this.getClass.getResource(path)
    if (resource == null) {
      throw new IllegalArgumentException(s"resource not found: $path")
    }
    val source = Source.fromURL(resource).getLines().mkString("\n")

    val api = new RamlModelBuilder().buildApi(source,path)

    val validationErrors = api.getValidationResults.mkString("\n")
    val apiV10 = api.getApiV10
    if (apiV10 == null) {
      fail(validationErrors)
    }
    else {
      println(validationErrors)
    }

    val gen = new InterfaceGenerator(apiV10, GeneratorOptions(packageName = "eu.inn.raml"))
    val result = gen.generate()

    result should include("package eu.inn.raml")
    val idx = result.indexOf("\nobject BookTag {")
    if (idx < 0) {
      println("=======================================================================")
      println(result)
      println("=======================================================================")
      fail("RAML generator doesn't contain permanent marker")
    }
    val resultPermanent = result.substring(idx)

    val resultPermanentNormalized = normalize(resultPermanent)
    val referenceValueNormalized = normalize(referenceValue)

    if (resultPermanentNormalized.indexOf(referenceValueNormalized) < 0) {
      println("=======================================================================")
      println(result)
      println("=======================================================================")
      val diff = new DiffMatchPatch
      val diffResult = diff.diffMain(resultPermanentNormalized, referenceValueNormalized, false)
      import JavaConversions._
      diffResult.foreach {
        case d: Diff if d.operation == Operation.EQUAL ⇒
          print(d.text)
        case d: Diff if d.operation == Operation.INSERT ⇒
          print("\n+++>")
          print(d.text)
          print("<+++\n")
        case d: Diff if d.operation == Operation.DELETE ⇒
          print("\n--->")
          print(d.text)
          print("<---\n")
      }
      fail("RAML generator doesn't return reference text")
    }
  }
}
