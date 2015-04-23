package eu.inn.hyperbus.protocol.annotations

import eu.inn.hyperbus.protocol.annotations.impl.AnnotationsMacro

import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly
import scala.language.experimental.macros

package impl {
class ContentTypeMarker(v: String) extends StaticAnnotation
}

@compileTimeOnly("enable macro paradise to expand macro annotations")
class contentType(v: String) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Unit = macro AnnotationsMacro.contentType
}
