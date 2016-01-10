package eu.inn.hyperbus.model.annotations

import eu.inn.hyperbus.model.{Body, UriParser}

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class request(v: String) extends StaticAnnotation {
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

  import c.universe._

  def updateRequestClass(uriPattern: String, existingClass: ClassDef, clzCompanion: Option[ModuleDef] = None): c.Expr[Any] = {
    val q"case class $className(..$fields) extends ..$bases { ..$body }" = existingClass

    val fieldsExcept = fields.filterNot { f ⇒
      f.name.toString == "correlationId" || f.name.toString == "messageId"
    }

    val (bodyFieldName, bodyType) = fields.flatMap { field ⇒
      field.tpt match {
        case i: Ident ⇒
          val typeName = i.name.toTypeName
          val fieldType = c.typecheck(q"(??? : $typeName)").tpe
          if (fieldType <:< typeOf[Body]) {
            Some((field.name, typeName))
          }
          else
            None
        case _ ⇒
          None
      }
    }.headOption.getOrElse {
      c.abort(c.enclosingPosition, "No Body parameter was found")
    }

    val uriParts = UriParser.extractParameters(uriPattern).map { arg ⇒
      q"$arg -> SpecificValue(this.${TermName(arg)}.toString)" // todo: remove toString if string, + inner fields?
    }

    val newClass = q"""
        @eu.inn.hyperbus.model.annotations.uri($uriPattern) case class $className(..$fieldsExcept,
          messageId: String,
          correlationId: String) extends ..$bases {
          ..$body

          import eu.inn.hyperbus.transport.api._
          lazy val uri = Uri(${className.toTermName}.uriPattern, UriParts(Map(..$uriParts)))
        }
      """

    val ctxVal = fresh("ctx")
    val bodyVal = fresh("body")
    val companionExtra = q"""
        def apply(..$fieldsExcept)(implicit contextFactory: eu.inn.hyperbus.model.MessagingContextFactory): $className = {
          val $ctxVal = contextFactory.newContext()
          ${className.toTermName}(..${fieldsExcept.map(_.name)},messageId = $ctxVal.messageId, correlationId = $ctxVal.correlationId)
        }

        def deserializer(requestHeader: eu.inn.hyperbus.serialization.RequestHeader, jsonParser: com.fasterxml.jackson.core.JsonParser): $className = {
          val $bodyVal = ${bodyType.toTermName}(requestHeader.contentType, jsonParser)

          //todo: extract uri parts!

          ${className.toTermName}(
            ..${fieldsExcept.filterNot(_.name==bodyFieldName).map{ field ⇒
                q"${field.name} = requestHeader.uri.parts.uriPartsMap(${field.name.toString}).specific"
            }},
            $bodyFieldName = $bodyVal,
            messageId = requestHeader.messageId,
            correlationId = requestHeader.correlationId.getOrElse(requestHeader.messageId)
          )
        }
        def apply(requestHeader: eu.inn.hyperbus.serialization.RequestHeader, jsonParser : com.fasterxml.jackson.core.JsonParser): $className =
          deserializer(requestHeader, jsonParser)
        def uriPattern = $uriPattern
    """

    println(companionExtra)

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

  private def getMethodAnnotation(t: c.Type): Option[String] =
    getStringAnnotation(t, typeOf[method])

  def updateClass(annotationArgument: Tree, existingClass: ClassDef, clzCompanion: Option[ModuleDef] = None): c.Expr[Any] = {
    getStringAnnotation(annotationArgument) map { uriPattern ⇒
      updateRequestClass(uriPattern, existingClass, clzCompanion)
    } getOrElse {
      c.abort(c.enclosingPosition, "Please provide uriPattern string argument for @response annotation")
    }
  }
}