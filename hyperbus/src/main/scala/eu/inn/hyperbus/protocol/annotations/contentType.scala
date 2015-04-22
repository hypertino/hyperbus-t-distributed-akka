package eu.inn.hyperbus.protocol.annotations

import eu.inn.hyperbus.protocol.annotations.impl.AnnotationsMacro

import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly
import scala.language.experimental.macros

trait ContentTypeMarker

@compileTimeOnly("enable macro paradise to expand macro annotations")
class contentType(v: String) extends StaticAnnotation with ContentTypeMarker{
  def macroTransform(annottees: Any*): Unit = macro AnnotationsMacro.contentType
}
