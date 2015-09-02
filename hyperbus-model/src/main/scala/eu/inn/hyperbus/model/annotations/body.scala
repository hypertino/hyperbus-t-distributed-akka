package eu.inn.hyperbus.model.annotations

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class body(v: String) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro BodyMacroImpl.body
}

private[annotations] object BodyMacroImpl {
  def body(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with BodyAnnotationMacroImpl
    bundle.run(annottees)
  }
}

private[annotations] trait BodyAnnotationMacroImpl extends AnnotationMacroImplBase {
  import c.universe._

  def updateClass(annotationArgument: Tree, existingClass: ClassDef, clzCompanion: Option[ModuleDef] = None): c.Expr[Any] = {
    val q"case class $className(..$fields) extends ..$bases { ..$body }" = existingClass

    val newClass = q"""
        @eu.inn.hyperbus.model.annotations.contentType($annotationArgument) case class $className(..$fields) extends ..$bases {
          ..$body
          def contentType = Some($annotationArgument)
          override def encode(outputStream: java.io.OutputStream) = {
            import eu.inn.hyperbus.serialization.MessageEncoder.bindOptions
            eu.inn.binders.json.SerializerFactory.findFactory().withStreamGenerator(outputStream) { serializer=>
              serializer.bind[$className](this)
            }
          }
        }
      """

    // check requestHeader
    val companionExtra = q"""
        def contentType = Some($annotationArgument)
        def decoder(contentType: Option[String], jsonParser : com.fasterxml.jackson.core.JsonParser): $className = {
          eu.inn.binders.json.SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
            deserializer.unbind[$className]
          }
        }
        def apply(contentType: Option[String], jsonParser : com.fasterxml.jackson.core.JsonParser): $className =
          decoder(contentType, jsonParser)
        """

    val newCompanion = clzCompanion map { existingCompanion =>
      val q"object $companion extends ..$bases { ..$body }" = existingCompanion
      q"""
          object $companion extends ..$bases {
            ..$body
            ..$companionExtra
          }
        """
    } getOrElse {
      q"""
        object ${className.toTermName} {
          ..$companionExtra
        }
      """
    }

    c.Expr(q"""
        $newClass
        $newCompanion
      """
    )
  }
}
