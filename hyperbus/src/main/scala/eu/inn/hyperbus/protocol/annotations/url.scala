package eu.inn.hyperbus.protocol.annotations

import eu.inn.hyperbus.protocol.annotations.impl.AnnotationsMacro

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros

trait UrlMarker

class url(v: String) extends StaticAnnotation with UrlMarker {
  def macroTransform(annottees: Any*): Unit = macro AnnotationsMacro.url
}
