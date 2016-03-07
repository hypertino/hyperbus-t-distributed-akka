object BookTag extends Enumeration {
  type BookTag = Value
  val NEW = Value("new")
  val BEST_SELLER = Value("best-seller")
  val CLASSICS = Value("classics")
}

case class BookProperties(publishYear: Int, issn: String, tag: BookTag.Value)

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

@request("/authors/{authorId}/books/{bookId}")
case class AuthorBookGet(
                          authorId: String,
                          bookId: String,
                          body: Query
                        ) extends Request[Query]
  with DefinedResponse[Ok[Book]]

@request("/authors/{authorId}/books/{bookId}")
case class AuthorBookPut(
                          authorId: String,
                          bookId: String,
                          body: Book
                        ) extends Request[Book]
  with DefinedResponse[(
    Ok[Body], Created[CreatedBody]
  )]