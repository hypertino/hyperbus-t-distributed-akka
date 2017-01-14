package com.hypertino.hyperbus.akkaservice.annotations


import scala.annotation.StaticAnnotation

class group(v: String) extends StaticAnnotation {
  //def macroTransform(annottees: Any*): Unit = macro AnnotationsMacro.method
}
