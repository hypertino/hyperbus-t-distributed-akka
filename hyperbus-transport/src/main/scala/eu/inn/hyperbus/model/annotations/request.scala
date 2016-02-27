package eu.inn.hyperbus.model.annotations

import eu.inn.hyperbus.model.Body
import eu.inn.hyperbus.transport.api.uri.UriParser

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.api.Trees
import scala.reflect.macros.blackbox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class request(method: String, uri: String) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro RequestMacro.request
}

private[annotations] object RequestMacro {
  def request(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with RequestAnnotationMacroImpl
    bundle.run(annottees)
  }
}

private[annotations] trait RequestAnnotationMacroImpl extends AnnotationMacroImplBase {
  val c: Context

  import c.universe._

  def updateClass(existingClass: ClassDef, clzCompanion: Option[ModuleDef] = None): c.Expr[Any] = {
    val (method, uriPattern) = c.prefix.tree match {
      case q"new request($method, $uri)" => {
        (c.Expr(method), c.eval[String](c.Expr(uri)))
      }
      case _ ⇒ c.abort(c.enclosingPosition, "Please provide arguments for @request annotation")
    }

    val q"case class $className(..$fields) extends ..$bases { ..$body }" = existingClass

    val fieldsExceptHeaders = fields.filterNot { f ⇒
      f.name.toString == "headers"
    }

    val (bodyFieldName, bodyType) = getBodyField(fields)

    val uriParts = UriParser.extractParameters(uriPattern).map { arg ⇒
      q"$arg -> this.${TermName(arg)}.toString" // todo: remove toString if string, + inner fields?
    }

    val uriPartsMap = if (uriParts.isEmpty) {
      q"Map.empty[String, String]"
    } else {
      q"Map(..$uriParts)"
    }

    val equalExpr = fieldsExceptHeaders.map(_.name).foldLeft(q"(o.headers == this.headers)") { (cap, name) ⇒
      q"(o.$name == this.$name) && $cap"
    }

    val cases = fieldsExceptHeaders.map(_.name).zipWithIndex.map { case (name, idx) ⇒
      cq"$idx => this.$name"
    } :+ cq"${fieldsExceptHeaders.size} => this.headers"

    val newClass =
      q"""
        @eu.inn.hyperbus.model.annotations.uri($uriPattern)
        @eu.inn.hyperbus.model.annotations.method($method)
        class $className(..$fieldsExceptHeaders,
          val headers: Map[String,Seq[String]], plain__init: Boolean)
          extends ..$bases with scala.Product {
          assertMethod(${className.toTermName}.method)
          ..$body
          import eu.inn.hyperbus.transport.api.uri._
          lazy val uri = Uri(${className.toTermName}.uriPattern, $uriPartsMap)

          def copy(
            ..${fieldsExceptHeaders.map { case ValDef(_, name, tpt, _) ⇒
              q"val $name: $tpt = this.$name"
            }},
            headers: Map[String, Seq[String]] = this.headers): $className = {
            ${className.toTermName}(..${fieldsExceptHeaders.map(_.name)}, eu.inn.hyperbus.model.Headers.plain(headers))
          }

          def canEqual(other: Any): Boolean = other.isInstanceOf[$className]

          override def equals(other: Any) = this.eq(other.asInstanceOf[AnyRef]) ||{
            other match {
              case o @ ${className.toTermName}(
                ..${fieldsExceptHeaders.map(f ⇒ q"${f.name}")},
                headers
              ) if $equalExpr ⇒ other.asInstanceOf[$className].canEqual(this)
              case _ => false
            }
          }

          override def hashCode: Int = scala.runtime.ScalaRunTime._hashCode(this)
          override def productArity: Int = ${fieldsExceptHeaders.size + 1}
          override def productElement(n: Int): Any = n match {
            case ..$cases
          }
        }
      """



    val ctxVal = fresh("ctx")
    val bodyVal = fresh("body")
    val companionExtra =
      q"""
        def apply(..$fieldsExceptHeaders, headers: eu.inn.hyperbus.model.Headers): $className = {
          new $className(..${fieldsExceptHeaders.map(_.name)},
            headers = new eu.inn.hyperbus.model.HeadersBuilder(headers)
              .withMethod(${className.toTermName}.method)
              .withContentType(body.contentType)
              .result(),
            plain__init = false
          )
        }

        def apply(..$fieldsExceptHeaders)
          (implicit mcx: eu.inn.hyperbus.model.MessagingContextFactory): $className =
          apply(..${fieldsExceptHeaders.map(_.name)}, eu.inn.hyperbus.model.Headers()(mcx))

        def apply(requestHeader: eu.inn.hyperbus.serialization.RequestHeader, jsonParser : com.fasterxml.jackson.core.JsonParser): $className = {
          val $bodyVal = ${bodyType.toTermName}(requestHeader.contentType, jsonParser)

          //todo: extract uri parts!

          new $className(
            ..${
              fieldsExceptHeaders.filterNot(_.name == bodyFieldName).map { field ⇒
                q"${field.name} = requestHeader.uri.args(${field.name.toString}).specific"
              }
            },
            $bodyFieldName = $bodyVal,
            headers = requestHeader.headers,
            plain__init = true
          )
        }

        def unapply(request: $className) = Some((
          ..${fieldsExceptHeaders.map(f ⇒ q"request.${f.name}")},
          request.headers
        ))

        def uriPattern = $uriPattern
        def method: String = $method
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

    val block = c.Expr(
      q"""
        $newClass
        $newCompanion
      """
    )
    //println(block)
    block
  }

  def getBodyField(fields: Seq[Trees#ValDef]): (TermName, TypeName) = {
    fields.flatMap { field ⇒
      field.tpt match {
        case i: Ident ⇒
          val typeName = i.name.toTypeName
          val fieldType = c.typecheck(q"(??? : $typeName)").tpe
          if (fieldType <:< typeOf[Body]) {
            Some((field.name.asInstanceOf[TermName], typeName))
          }
          else
            None
        case _ ⇒
          None
      }
    }.headOption.getOrElse {
      c.abort(c.enclosingPosition, "No Body parameter was found")
    }
  }
}