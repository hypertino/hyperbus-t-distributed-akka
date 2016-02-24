package eu.inn.hyperbus.model

import java.io.OutputStream

import eu.inn.binders.dynamic.{Null, Value}
import eu.inn.hyperbus.IdGenerator

trait ErrorBody extends Body {
  def code: String

  def description: Option[String]

  def errorId: String

  def extra: Value

  def message: String
}

object ErrorBody {
  def apply(code: String,
            description: Option[String] = None,
            errorId: String = IdGenerator.create(),
            extra: Value = Null,
            contentType: Option[String] = None): ErrorBody =
    ErrorBodyContainer(code, description, errorId, extra, contentType)

  def unapply(errorBody: ErrorBody) = Some(
    (errorBody.code, errorBody.description, errorBody.errorId, errorBody.extra, errorBody.contentType)
  )

  def deserializer(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): ErrorBody = {
    import eu.inn.binders._
    eu.inn.binders.json.SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      deserializer.unbind[ErrorBodyContainer].copy(contentType = contentType)
    }
  }

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): ErrorBody = deserializer(contentType, jsonParser)

  /*def apply(jsonParser : com.fasterxml.jackson.core.JsonParser): ErrorBody = {
    apply(jsonParser, )
  }*/
}

private[model] case class ErrorBodyContainer(code: String,
                                                description: Option[String],
                                                errorId: String,
                                                extra: Value,
                                                contentType: Option[String]) extends ErrorBody {
  def message = code + description.map(": " + _).getOrElse("") + ". #" + errorId

  def serialize(outputStream: OutputStream): Unit = {
    import eu.inn.binders._
    import eu.inn.hyperbus.serialization.MessageSerializer.bindOptions
    implicit val $fVal = new eu.inn.hyperbus.serialization.JsonHalSerializerFactory[eu.inn.binders.naming.PlainConverter]
    eu.inn.binders.json.SerializerFactory.findFactory().withStreamGenerator(outputStream) { serializer =>
      serializer.bind(this.copy(contentType = None)) // find other way to skip contentType
    }
  }
}
