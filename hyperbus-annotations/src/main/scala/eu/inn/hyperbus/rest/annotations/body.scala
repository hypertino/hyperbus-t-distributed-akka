package eu.inn.hyperbus.rest.annotations

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class body(v: String) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro BodyMacroImpl.contentType
}

private[annotations] object BodyMacroImpl {
  def contentType(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
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
        @eu.inn.hyperbus.rest.annotations.contentType($annotationArgument) case class $className(..$fields) extends ..$bases {
          ..$body
          def contentType = Some($annotationArgument)
        }
      """

    val newCompanion = clzCompanion map { existingCompanion =>
      val q"object $companion extends ..$bases { ..$body }" = existingCompanion
      q"""
          object $companion extends ..$bases {
            ..$body
            def contentType = Some($annotationArgument)
          }
        """
    } getOrElse {
      q"""
        object ${className.toTermName} {
          def contentType = Some($annotationArgument)
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
