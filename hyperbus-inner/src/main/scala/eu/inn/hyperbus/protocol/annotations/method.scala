package eu.inn.hyperbus.protocol.annotations

import scala.annotation.{StaticAnnotation}

class method(v: String) extends StaticAnnotation {
  //def macroTransform(annottees: Any*): Unit = macro AnnotationsMacro.method
}
