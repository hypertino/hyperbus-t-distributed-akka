package eu.inn.hyperbus.protocol.annotations.impl

import eu.inn.hyperbus.protocol.annotations.contentType

import scala.reflect.macros.whitebox.Context

private[hyperbus] object AnnotationsMacro {

  def reportInvalidAnnotationTarget(c: Context) {
    c.error(c.enclosingPosition, "This annotation can only be used on class")
  }

  def contentType(c: Context)(annottees: c.Expr[Any]*): c.Expr[Unit] = {
    import c.universe._
    val (_ :: methodResult :: _) = c.prefix.tree.children
    defineMethod(c)("contentType",q"""Some($methodResult)""",annottees)
  }

  def url(c: Context)(annottees: c.Expr[Any]*): c.Expr[Unit] = {
    import c.universe._
    val (_ :: methodResult :: _) = c.prefix.tree.children
    defineMethod(c)("url",q"""$methodResult""",annottees)
  }

  private[this] def defineMethod(c: Context)
                                (methodName: String, result: c.Tree, annottees: Seq[c.Expr[Any]]): c.Expr[Unit] = {
    import c.universe._
    val inputs = annottees.map(_.tree).toList
    //println(inputs)
    val (outputs, rest) = inputs match {
      case (cls: ClassDef) :: rest => {
        val defContentType = DefDef(Modifiers(Flag.OVERRIDE),
          TermName(methodName), List(), List(), TypeTree(), result)

        val ClassDef(mods, name, tparams, Template(parents, self, body)) = cls
        val newClass = ClassDef(mods, name, tparams, Template(parents, self, body ++ Seq(defContentType)))
        (newClass, rest)
      }
      case _ => reportInvalidAnnotationTarget(c); (EmptyTree, inputs)
    }
    c.Expr[Unit](q"..$outputs")
  }
}
