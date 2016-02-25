package eu.inn.hyperbus.model.annotations

import eu.inn.hyperbus.model.{Request, Body}
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

    val fieldsExcept = fields.filterNot { f ⇒
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
    val newClass = q"""
        @eu.inn.hyperbus.model.annotations.uri($uriPattern)
        @eu.inn.hyperbus.model.annotations.method($method)
        case class $className(..$fieldsExcept,
          headers: Map[String, Seq[String]]) extends ..$bases {
          assertMethod(${className.toTermName}.method)
          ..$body
          import eu.inn.hyperbus.transport.api.uri._
          lazy val uri = Uri(${className.toTermName}.uriPattern, $uriPartsMap)
        }
      """

    val ctxVal = fresh("ctx")
    val bodyVal = fresh("body")
    val companionExtra = q"""
        def apply(..$fieldsExcept, headersBuilder: eu.inn.hyperbus.model.HeadersBuilder)
          (implicit contextFactory: eu.inn.hyperbus.model.MessagingContextFactory): $className = {
          ${className.toTermName}(..${fieldsExcept.map(_.name)},
            headers = headersBuilder
              .withMethod(${className.toTermName}.method)
              .withContentType(body.contentType)
              .withContext(contextFactory)
              .result()
          )
        }

        def apply(..$fieldsExcept)
          (implicit contextFactory: eu.inn.hyperbus.model.MessagingContextFactory): $className =
          apply(..${fieldsExcept.map(_.name)}, new eu.inn.hyperbus.model.HeadersBuilder)(contextFactory)

        def apply(requestHeader: eu.inn.hyperbus.serialization.RequestHeader, jsonParser : com.fasterxml.jackson.core.JsonParser): $className = {
          val $bodyVal = ${bodyType.toTermName}(requestHeader.contentType, jsonParser)

          //todo: extract uri parts!

          ${className.toTermName}(
            ..${fieldsExcept.filterNot(_.name==bodyFieldName).map{ field ⇒
                q"${field.name} = requestHeader.uri.args(${field.name.toString}).specific"
            }},
            $bodyFieldName = $bodyVal,
            headers = requestHeader.headers
          )
        }

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

    val block = c.Expr(q"""
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