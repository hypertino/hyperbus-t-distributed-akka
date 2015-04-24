package eu.inn.hyperbus.protocol.annotations.impl

import scala.reflect.macros.whitebox.Context

private[hyperbus] object AnnotationsMacro {

  def reportInvalidAnnotationTarget(c: Context) {
    c.error(c.enclosingPosition, "This annotation can only be used on class")
  }

  def contentType(c: Context)(annottees: c.Expr[Any]*): c.Expr[Unit] = {
    import c.universe._
    val (_ :: methodResult :: _) = c.prefix.tree.children
    defineMethod(c)("contentType",
      q"new eu.inn.hyperbus.protocol.annotations.impl.ContentTypeMarker($methodResult)",
      q"""Some($methodResult)""",
      annottees
    )
  }

  def url(c: Context)(annottees: c.Expr[Any]*): c.Expr[Unit] = {
    import c.universe._
    val (_ :: methodResult :: _) = c.prefix.tree.children
    defineMethod(c)("url",
      q"new eu.inn.hyperbus.protocol.annotations.impl.UrlMarker($methodResult)",
      q"""$methodResult""",
      annottees
    )
  }

  /*def method(c: Context)(annottees: c.Expr[Any]*): c.Expr[Unit] = {
    import c.universe._
    val (_ :: methodResult :: _) = c.prefix.tree.children
    defineMethod(c)("url",
      q"new eu.inn.hyperbus.protocol.annotations.impl.MethodMarker($methodResult)",
      q"""$methodResult""",
      annottees
    )
  }*/

  private[this] def defineMethod(c: Context)
                                (methodName: String, annotation: c.Tree, result: c.Tree, annottees: Seq[c.Expr[Any]]): c.Expr[Unit] = {
    import c.universe._

    val inputs = annottees.map(_.tree).toList
    val (outputs, rest) = inputs match {
      case (cls: ClassDef) :: tail =>
        val ClassDef(Modifiers(modFlugs, uName, annotations), name, tparams, Template(parents, self, body)) = cls
        val defMethod = DefDef(Modifiers(Flag.OVERRIDE), TermName(methodName), List(), List(), TypeTree(), result)
        val newClass = ClassDef(Modifiers(modFlugs, uName, annotations ++ Seq(annotation)),
          name, tparams, Template(parents, self, body ++ Seq(defMethod)))
        (newClass, tail)
      case _ => reportInvalidAnnotationTarget(c); (EmptyTree, inputs)
    }
    c.Expr[Unit](q"..$outputs")
  }
}
