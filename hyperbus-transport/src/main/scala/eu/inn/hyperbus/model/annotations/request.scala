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

    val classFields: Seq[ValDef] = if (fields.exists(_.name.toString == "headers")) fields else {
      fields :+ q"val headers: Map[String,Seq[String]]"
    }

    val (bodyFieldName, bodyType) = getBodyField(fields)
    //println(s"rhs = $defaultValue, ${defaultValue.isEmpty}")

    val uriParts = UriParser.extractParameters(uriPattern).map { arg ⇒
      q"$arg -> this.${TermName(arg)}.toString" // todo: remove toString if string, + inner fields?
    }

    val uriPartsMap = if (uriParts.isEmpty) {
      q"Map.empty[String, String]"
    } else {
      q"Map(..$uriParts)"
    }

    val equalExpr = classFields.map(_.name).foldLeft[Tree](q"true") { (cap, name) ⇒
      q"(o.$name == this.$name) && $cap"
    }

    val cases = classFields.map(_.name).zipWithIndex.map { case (name, idx) ⇒
      cq"$idx => this.$name"
    }

    val fieldsNoHeaders = classFields.filterNot(_.name.toString == "headers").map { field: ValDef ⇒
      val ft = getFieldType(field)
      // todo: the following is hack. due to compiler restriction, defval can't be provided as def field arg
      // it's also possible to explore field-type if it has a default constructor, companion with apply ?
      val rhs = ft.toString match {
        case "eu.inn.hyperbus.model.EmptyBody" ⇒ q"eu.inn.hyperbus.model.EmptyBody"
        case "eu.inn.hyperbus.model.QueryBody" ⇒ q"eu.inn.hyperbus.model.QueryBody()"
        case other => field.rhs
      }
      ValDef(field.mods, field.name, field.tpt, rhs)
    }

    val newClass =
      q"""
        @eu.inn.hyperbus.model.annotations.uri($uriPattern)
        @eu.inn.hyperbus.model.annotations.method($method)
        class $className(..${classFields.map(stripDefaultValue)}, plain__init: Boolean)
          extends ..$bases with scala.Product {
          assertMethod(${className.toTermName}.method)
          ..$body
          import eu.inn.hyperbus.transport.api.uri._
          lazy val uri = Uri(${className.toTermName}.uriPattern, $uriPartsMap)

          def copy(
            ..${classFields.map { case ValDef(_, name, tpt, _) ⇒
              q"val $name: $tpt = this.$name"
            }}): $className = {
            ${className.toTermName}(..${fieldsNoHeaders.map(_.name)}, headers = eu.inn.hyperbus.model.Headers.plain(headers))
          }

          def canEqual(other: Any): Boolean = other.isInstanceOf[$className]

          override def equals(other: Any) = this.eq(other.asInstanceOf[AnyRef]) ||{
            other match {
              case o @ ${className.toTermName}(
                ..${classFields.map(f ⇒ q"${f.name}")}
              ) if $equalExpr ⇒ other.asInstanceOf[$className].canEqual(this)
              case _ => false
            }
          }

          override def hashCode: Int = scala.runtime.ScalaRunTime._hashCode(this)
          override def productArity: Int = ${classFields.size}
          override def productElement(n: Int): Any = n match {
            case ..$cases
            case _ => throw new IndexOutOfBoundsException(n.toString())
          }
        }
      """

    val fieldsWithDefVal = fieldsNoHeaders.filter(_.rhs.nonEmpty) :+
      q"val headers: eu.inn.hyperbus.model.Headers = eu.inn.hyperbus.model.Headers()(mcx)"

    val defMethods = fieldsWithDefVal.map { case currentField: ValDef ⇒
      val fmap = fieldsNoHeaders.foldLeft((Seq.empty[Tree], Seq.empty[Tree], false)) { case ((seqFields, seqVals, withDefaultValue), f) ⇒
        val defV = withDefaultValue || f.name == currentField.name

        (seqFields ++ {if (!defV) Seq(stripDefaultValue(f)) else Seq.empty},
        seqVals :+ {if (defV) q"${f.name} = ${f.rhs}" else  q"${f.name}"},
          defV)
      }
      //val name = TermName(if(fmap._1.isEmpty) "em" else "apply")
      q"""def apply(
            ..${fmap._1}
         )(implicit mcx: eu.inn.hyperbus.model.MessagingContextFactory): $className =
         apply(..${fmap._2}, headers = eu.inn.hyperbus.model.Headers()(mcx))"""
    }

    //println(defMethods)

    val ctxVal = fresh("ctx")
    val bodyVal = fresh("body")
    val companionExtra =
      q"""
        def apply(..${fieldsNoHeaders.map(stripDefaultValue)}, headers: eu.inn.hyperbus.model.Headers): $className = {
          new $className(..${fieldsNoHeaders.map(_.name)},
            headers = new eu.inn.hyperbus.model.HeadersBuilder(headers)
              .withMethod(${className.toTermName}.method)
              .withContentType(body.contentType)
              .result(),
            plain__init = false
          )
        }

        ..$defMethods

        def apply(requestHeader: eu.inn.hyperbus.serialization.RequestHeader, jsonParser : com.fasterxml.jackson.core.JsonParser): $className = {
          val $bodyVal = ${bodyType.toTermName}(requestHeader.contentType, jsonParser)

          //todo: extract uri parts!

          new $className(
            ..${
                fieldsNoHeaders.filterNot(_.name == bodyFieldName).map { field ⇒
                q"${field.name} = requestHeader.uri.args(${field.name.toString}).specific"
              }
            },
            $bodyFieldName = $bodyVal,
            headers = requestHeader.headers,
            plain__init = true
          )
        }

        def unapply(request: $className) = Some((
          ..${classFields.map(f ⇒ q"request.${f.name}")}
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

  def stripDefaultValue(field: ValDef): ValDef = ValDef(field.mods, field.name, field.tpt, EmptyTree)

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

  def getFieldType(field: Trees#ValDef): Type = field.tpt match {
    case i: Ident ⇒
      val typeName = i.name.toTypeName
      c.typecheck(q"(??? : $typeName)").tpe
  }
}