package eu.inn.hyperbus.model.annotations

import scala.reflect.macros.blackbox.Context

private[annotations] trait AnnotationMacroImplBase {
  val c: Context

  import c.universe._

  def run(annottees: Seq[c.Expr[Any]]): c.Expr[Any] = {
    annottees.map(_.tree) match {
      case (clz: ClassDef) :: Nil => updateClass(clz)
      case (clz: ClassDef) :: (clzCompanion: ModuleDef) :: Nil => updateClass(clz, Some(clzCompanion))
      case _ => invalidAnnottee()
    }
  }

  protected def updateClass(existingClass: ClassDef, existingCompanion: Option[ModuleDef] = None): c.Expr[Any]

  protected def invalidAnnottee() = c.abort(c.enclosingPosition, "This annotation can only be used on class")

  protected def fresh(prefix: String): TermName = TermName(c.freshName(prefix))
}
