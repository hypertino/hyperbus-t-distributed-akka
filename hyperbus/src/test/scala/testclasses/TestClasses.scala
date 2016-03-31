package testclasses

import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.{EmptyBody, QueryBody, _}

@body("test-1")
case class TestBody1(resourceData: String) extends Body

@body("test-2")
case class TestBody2(resourceData: Long) extends Body

@body("created-body")
case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: Links.LinksMap = Links.location("/resources/{resourceId}", templated = true))
  extends CreatedBody

@body("some-another-body")
case class TestAnotherBody(resourceId: String) extends Body

@body
case class TestBodyNoContentType(resourceData: String) extends Body

@request(Method.POST, "/resources")
case class TestPost1(body: TestBody1) extends Request[TestBody1]
  with DefinedResponse[Created[TestCreatedBody]]

@request(Method.POST, "/resources")
case class TestPost2(body: TestBody2) extends Request[TestBody2]
  with DefinedResponse[Created[TestCreatedBody]]

@request(Method.POST, "/resources")
case class TestPost3(body: TestBody2) extends Request[TestBody2]
  with DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]

@request(Method.POST, "/empty")
case class TestPostWithNoContent(body: TestBody1) extends Request[TestBody1]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.POST, "/empty")
case class StaticPostWithDynamicBody(body: DynamicBody) extends Request[DynamicBody]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.POST, "/empty")
case class StaticPostWithEmptyBody(body: EmptyBody) extends Request[EmptyBody]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.GET, "/empty")
case class StaticGetWithQuery(body: QueryBody) extends Request[QueryBody]
  with DefinedResponse[Ok[DynamicBody]]

@request(Method.POST, "/content-body-not-specified")
case class StaticPostBodyWithoutContentType(body: TestBodyNoContentType) extends Request[TestBodyNoContentType]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.PUT, "/2resp")
case class TestPostWith2Responses(body: TestBody1) extends Request[TestBody1]
  with DefinedResponse[(Created[TestCreatedBody], Ok[TestAnotherBody])]


@body("some-transaction")
case class SomeTransaction(transactionId: String) extends Body

@body("some-transaction-created")
case class SomeTransactionCreated(
                                      transactionId: String,
                                      path: String,
                                      @fieldName("_links") links: Links.LinksMap
                                    ) extends Body with Links with CreatedBody

@request(Method.PUT, "/some/{path:*}")
case class SomeContentPut(
                              path: String,
                              body: DynamicBody
                            ) extends Request[DynamicBody]
  with DefinedResponse[(
    Ok[SomeTransaction],
      Created[SomeTransactionCreated]
    )]



