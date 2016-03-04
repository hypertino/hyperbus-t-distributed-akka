package eu.inn.hyperbus.model

import java.io.OutputStream

import eu.inn.binders.dynamic.{Text, Obj, Null, Value}
import eu.inn.hyperbus.IdGenerator

trait ErrorBody extends DynamicBody {
  def code: String

  def description: Option[String]

  def errorId: String

  def extra: Value

  def message: String

  def content = Obj(Map[String,Value](
      "code" → code,
      "errorId" → errorId
    )
      ++ description.map(s ⇒ "description" → Text(s))
      ++ contentType.map(s ⇒ "contentType" → Text(s))
      ++ {if(extra.isDefined) Some("extra" → extra) else None}
  )

  def copyErrorBody(
           code: String = this.code,
           description: Option[String] = this.description,
           errorId: String = this.errorId,
           extra: Value = this.extra,
           message: String = this.message,
           contentType: Option[String] = this.contentType
          ): ErrorBody = {
    ErrorBodyContainer(code, description, errorId, extra, contentType)
  }
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

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): ErrorBody = {
    import eu.inn.binders._
    eu.inn.binders.json.SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      deserializer.unbind[ErrorBodyContainer].copyErrorBody(contentType = contentType)
    }
  }
}

private[model] case class ErrorBodyContainer(code: String,
                                             description: Option[String],
                                             errorId: String,
                                             extra: Value,
                                             contentType: Option[String]) extends ErrorBody {
  def message = code + description.map(": " + _).getOrElse("") + ". #" + errorId

  override def serialize(outputStream: OutputStream): Unit = {
    import eu.inn.binders._
    implicit val bindOptions = eu.inn.hyperbus.serialization.MessageSerializer.bindOptions
    implicit val jsonSerializerFactory = new eu.inn.hyperbus.serialization.JsonHalSerializerFactory[eu.inn.binders.naming.PlainConverter]
    eu.inn.binders.json.SerializerFactory.findFactory().withStreamGenerator(outputStream) { serializer =>
      serializer.bind(this.copyErrorBody(contentType = None)) // find other way to skip contentType
    }
  }
}
