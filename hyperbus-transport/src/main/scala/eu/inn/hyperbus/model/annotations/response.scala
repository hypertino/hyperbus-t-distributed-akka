package eu.inn.hyperbus.model.annotations

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class response(statusCode: Int) extends StaticAnnotation {
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
    val statusCode = c.prefix.tree match {
      case q"new response($statusCode)" => c.Expr(statusCode)
      case _ ⇒ c.abort(c.enclosingPosition, "Please provide arguments for @response annotation")
    }

    val q"case class $className[..$typeArgs](..$fields) extends ..$bases { ..$body }" = existingClass

    if (typeArgs.size != 1) {
      c.abort(c.enclosingPosition, "One type parameter is expected for a response: [T <: Body]")
    }

    val methodTypeArgs = typeArgs.map { t: TypeDef ⇒
      TypeDef(Modifiers(), t.name, t.tparams, t.rhs)
    }
    val classTypeNames = typeArgs.map { t: TypeDef ⇒
      t.name
    }
    val upperBound = typeArgs.head.asInstanceOf[TypeDef].rhs match {
      case TypeBoundsTree(lower, upper) ⇒ upper
      case _ ⇒ c.abort(c.enclosingPosition, "Type bounds aren't found: [T <: Body]")
    }

    val fieldsExceptHeaders = fields.filterNot(_.name.decodedName.toString == "headers")

    val equalExpr = fieldsExceptHeaders.map(_.name).foldLeft(q"(o.headers == this.headers)") { (cap, name) ⇒
      q"(o.$name == this.$name) && $cap"
    }

    val cases = fieldsExceptHeaders.map(_.name).zipWithIndex.map { case (name, idx) ⇒
      cq"$idx => this.$name"
    } :+ cq"${fieldsExceptHeaders.size} => this.headers"

    // S -> fresh term
    val newClass =
      q"""
        @eu.inn.hyperbus.model.annotations.statusCode($statusCode)
        class $className[..$typeArgs](..$fieldsExceptHeaders,
          val headers: Map[String,Seq[String]], plain__init: Boolean)
          extends ..$bases with scala.Product {
          def statusCode: Int = ${className.toTermName}.statusCode

          def copy[S <: $upperBound](body: S = this.body, headers: Map[String, Seq[String]] = this.headers)
            (implicit mcx: eu.inn.hyperbus.model.MessagingContextFactory): $className[S] = {
            ${className.toTermName}[S](body, eu.inn.hyperbus.model.Headers.plain(headers))
          }

          def canEqual(other: Any): Boolean = other.isInstanceOf[$className[_ <: $upperBound]]

          override def equals(other: Any) = this.eq(other.asInstanceOf[AnyRef]) || {
            if (other.isInstanceOf[$className[_ <: $upperBound]]) {
              val o = other.asInstanceOf[$className[_ <: $upperBound]]
              $equalExpr
            } else {
              false
            }
          }

          override def hashCode: Int = scala.runtime.ScalaRunTime._hashCode(this)
          override def productArity: Int = ${fieldsExceptHeaders.size + 1}
          override def productElement(n: Int): Any = n match {
            case ..$cases
            case _ => throw new IndexOutOfBoundsException(n.toString())
          }
        }
      """

    val ctxVal = fresh("ctx")
    val companionExtra =
      q"""
        def statusCode: Int = $statusCode

        def apply[..$methodTypeArgs](..$fieldsExceptHeaders, headers: eu.inn.hyperbus.model.Headers):
          $className[..$classTypeNames] = {
          new $className[..$classTypeNames](..${fieldsExceptHeaders.map(_.name)},
            headers = new eu.inn.hyperbus.model.HeadersBuilder(headers)
              .withContentType(body.contentType)
              .result(),
            plain__init = false
          )
        }

        def apply[..$methodTypeArgs](..$fieldsExceptHeaders)
          (implicit mcx: eu.inn.hyperbus.model.MessagingContextFactory): $className[..$classTypeNames]
          = apply(..${fieldsExceptHeaders.map(_.name)}, eu.inn.hyperbus.model.Headers()(mcx))

        def unapply[..$methodTypeArgs](response: $className[..$classTypeNames]) = Some(
          (..${fieldsExceptHeaders.map(f ⇒ q"response.${f.name}")},response.headers)
        )
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

    val block = q"""
        $newClass
        $newCompanion
      """

    //println(block)

    c.Expr(
      block
    )
  }
}