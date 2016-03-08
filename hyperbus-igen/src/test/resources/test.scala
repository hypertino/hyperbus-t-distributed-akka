object BookTag {
  type StringEnum = String
  val NEW = "new"
  val BEST_SELLER = "best-seller"
  val CLASSICS = "classics"
  lazy val toSeq = Set(NEW,BEST_SELLER,CLASSICS)
  lazy val toSet = toSeq.toSeq
}

case class BookProperties(publishYear: Int, issn: String, tag: BookTag.StringEnum)

@body("eu-inn-book")
case class Book(
                 bookId: String,
                 authorId: String,
                 bookName: String,
                 authorName: String,
                 bookProperties: BookProperties,
                 @fieldName("_links") links: Links.LinksMap = Book.defaultLinks
               ) extends Body

object Book {
  val selfLinkPattern = "/authors/{authorId}/books/{bookId}"
  val defaultLinks = Links(selfLinkPattern, templated = true)
}

@request(Method.GET, "/authors/{authorId}/books/{bookId}")
case class AuthorBookGet(
                          authorId: String,
                          bookId: String,
                          body: QueryBody
                        ) extends Request[QueryBody]
  with DefinedResponse[Ok[Book]]

@request(Method.PUT, "/authors/{authorId}/books/{bookId}")
case class AuthorBookPut(
                          authorId: String,
                          bookId: String,
                          body: Book
                        ) extends Request[Book]
  with DefinedResponse[(
    Ok[Body], Created[CreatedBody]
  )]