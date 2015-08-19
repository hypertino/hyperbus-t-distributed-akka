package eu.inn.hyperbus.rest.annotations

import scala.reflect.macros.blackbox.Context

private[hyperbus] object AnnotationsMacro {

  def reportInvalidAnnotationTarget(c: Context) {
    c.error(c.enclosingPosition, "This annotation can only be used on class")
  }

  def contentType(c: Context)(annottees: c.Expr[Any]*): c.Expr[Unit] = {
    import c.universe._
    val (_ :: methodResult :: _) = c.prefix.tree.children
    defineMethod(c)("contentType",
      q"new eu.inn.hyperbus.rest.annotations.contentTypeMarker($methodResult)",
      q"""Some($methodResult)""",
      annottees
    )
  }

  def request(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with RequestAnnotationMacroImpl
    bundle.run(annottees)
  }

  private[this] def defineMethod(c: Context)
                                (methodName: String, annotation: c.Tree, result: c.Tree, annottees: Seq[c.Expr[Any]]): c.Expr[Unit] = {
    import c.universe._

    val inputs = annottees.map(_.tree).toList
    val (outputs, rest) = inputs match {
      case (cls: ClassDef) :: tail =>
        val ClassDef(Modifiers(modFlugs, uName, annotations), name, tparams, Template(parents, self, body)) = cls
        val defMethod = DefDef(Modifiers(Flag.OVERRIDE), TermName(methodName), List.empty, List.empty, TypeTree(), result)
        val newClass = ClassDef(Modifiers(modFlugs, uName, annotations ++ Seq(annotation)),
          name, tparams, Template(parents, self, body ++ Seq(defMethod)))
        (newClass, tail)
      case _ => reportInvalidAnnotationTarget(c); (EmptyTree, inputs)
    }
    c.Expr[Unit](q"..$outputs")
  }
}

private[hyperbus] trait AnnotationMacroImplBase {
  val c: Context

  import c.universe._

  def run(annottees: Seq[c.Expr[Any]]): c.Expr[Any] = {
    val (_ :: annotationArgument :: _) = c.prefix.tree.children
    annottees.map(_.tree) match {
      case (clz: ClassDef) :: Nil => updateClass(annotationArgument, clz)
      case (clz: ClassDef) :: (clzCompanion: ModuleDef) :: Nil => updateClass(annotationArgument, clz, Some(clzCompanion))
      case _ => invalidAnnottee()
    }
  }

  def updateClass(annotationArgument: Tree, existingClass: ClassDef, existingCompanion: Option[ModuleDef] = None): c.Expr[Any]
  def invalidAnnottee() = c.abort(c.enclosingPosition, "This annotation can only be used on class")
}

private[hyperbus] trait RequestAnnotationMacroImpl extends AnnotationMacroImplBase {
  import c.universe._

  def updateClass(annotationArgument: Tree, existingClass: ClassDef, clzCompanion: Option[ModuleDef] = None): c.Expr[Any] = {
    val q"case class $className(..$fields) extends ..$bases { ..$body }" = existingClass

    val fieldsExcept = fields.filterNot { f ⇒
      f.name.toString == "correlationId" || f.name.toString == "messageId"
    }

    val newClass = q"""
        @eu.inn.hyperbus.rest.annotations.url($annotationArgument) case class $className(..$fieldsExcept,
          messageId: String,
          correlationId: Option[String]) extends ..$bases {
          ..$body
          def url = $annotationArgument
        }
      """

    val newCompanion = clzCompanion map { existingCompanion =>
      val q"object $companion extends ..$bases { ..$body }" = existingCompanion
      q"""
          object $companion extends ..$bases {
            ..$body
            def url = $annotationArgument
          }
        """
    } getOrElse {
      q"""
        object ${className.toTermName} {
          def apply(..$fieldsExcept)
                   (implicit context: eu.inn.hyperbus.rest.MessagingContext): $className =
                   ${className.toTermName}(
                      ..${fieldsExcept.map(_.name)},
                      messageId = eu.inn.hyperbus.utils.IdUtils.createId,
                      correlationId = context.correlationId
                   )

          def apply(..$fieldsExcept, messageId: String)
                   (implicit context: eu.inn.hyperbus.rest.MessagingContext): $className =
                   ${className.toTermName}(..${fieldsExcept.map(_.name)}, messageId = messageId, correlationId = context.correlationId)
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