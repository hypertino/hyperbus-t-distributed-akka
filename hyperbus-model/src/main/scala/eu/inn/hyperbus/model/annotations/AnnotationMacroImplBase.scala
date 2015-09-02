package eu.inn.hyperbus.model.annotations

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

  protected def updateClass(annotationArgument: Tree, existingClass: ClassDef, existingCompanion: Option[ModuleDef] = None): c.Expr[Any]

  protected def invalidAnnottee() = c.abort(c.enclosingPosition, "This annotation can only be used on class")

  protected def getStringAnnotation(annotationArgument: Tree): Option[String] = {
    annotationArgument match {
      case Literal(Constant(s: String)) => Some(s)
      case _ => None
    }
  }

  protected def getStringAnnotation(in: Type, atype: c.Type): Option[String] = {
    in.baseClasses.flatMap { baseSymbol =>
      baseSymbol.annotations.find { a =>
        a.tree.tpe <:< atype
      } flatMap {
        annotation => annotation.tree.children.tail.head match {
          case Literal(Constant(s: String)) => Some(s)
          case _ => None
        }
      }
    }.headOption
  }
}
