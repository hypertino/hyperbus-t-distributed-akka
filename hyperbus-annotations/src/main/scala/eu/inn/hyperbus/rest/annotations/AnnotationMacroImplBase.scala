package eu.inn.hyperbus.rest.annotations

import scala.reflect.macros.blackbox.Context

private[annotations] trait AnnotationMacroImplBase {
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
