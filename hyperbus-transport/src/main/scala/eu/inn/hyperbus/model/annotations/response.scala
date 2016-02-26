package eu.inn.hyperbus.model.annotations

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class response(status: Int) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro ResponseMacro.response
}

private[annotations] object ResponseMacro {
  def response(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with ResponseAnnotationMacroImpl
    bundle.run(annottees)
  }
}

private[annotations] trait ResponseAnnotationMacroImpl extends AnnotationMacroImplBase {

  import c.universe._

  def updateClass(existingClass: ClassDef, clzCompanion: Option[ModuleDef] = None): c.Expr[Any] = {
    val status = c.prefix.tree match {
      case q"new response($status)" => c.Expr(status)
      case _ ⇒ c.abort(c.enclosingPosition, "Please provide arguments for @response annotation")
    }

    val q"case class $className[..$typeArgs](..$fields) extends ..$bases { ..$body }" = existingClass

    val fieldsExceptHeaders = fields.filterNot { f ⇒
      f.name == "headers"
    }

    val methodTypeArgs = typeArgs.map { t: TypeDef ⇒
      TypeDef(Modifiers(), t.name, t.tparams, t.rhs)
    }
    val classTypeNames = typeArgs.map { t: TypeDef ⇒
      t.name
    }

    val newClass =
      q"""
        @eu.inn.hyperbus.model.annotations.status($status)
        case class $className[..$typeArgs](..$fieldsExceptHeaders,
                                           headers: Map[String, Seq[String]]) extends ..$bases {
          ..$body
          def status: Int = ${className.toTermName}.status
        }
      """

    val ctxVal = fresh("ctx")
    val companionExtra =
      q"""
        def apply[..$methodTypeArgs](..$fieldsExceptHeaders, headersBuilder: eu.inn.hyperbus.model.HeadersBuilder)
          (implicit contextFactory: eu.inn.hyperbus.model.MessagingContextFactory): $className[..$classTypeNames] = {
          ${className.toTermName}[..$classTypeNames](..${fieldsExceptHeaders.map(_.name)},
            headers = headersBuilder
              .withContext(contextFactory)
              .withContentType(body.contentType)
              .result()
          )
        }

        def apply[..$methodTypeArgs](..$fieldsExceptHeaders)
          (implicit contextFactory: eu.inn.hyperbus.model.MessagingContextFactory): $className[..$classTypeNames]
          = apply(..${fieldsExceptHeaders.map(_.name)}, new eu.inn.hyperbus.model.HeadersBuilder)(contextFactory)

        def status: Int = $status
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

    c.Expr(
      q"""
        $newClass
        $newCompanion
      """
    )
  }
}