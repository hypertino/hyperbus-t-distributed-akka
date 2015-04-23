package eu.inn.hyperbus.protocol.annotations

import eu.inn.hyperbus.protocol.annotations.impl.AnnotationsMacro

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros

package impl {
class UrlMarker(v: String) extends StaticAnnotation
}

@compileTimeOnly("enable macro paradise to expand macro annotations")
class url(v: String) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Unit = macro AnnotationsMacro.url
}
