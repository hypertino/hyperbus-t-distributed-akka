package eu.inn.hyperbus.util
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

object StringSerializer {
  def serializeToString(serializable: Any, encoding: String): String = macro StringSerializerImpl.serializeToString
  def serializeToString(serializable: Any): String = macro StringSerializerImpl.serializeToStringUtf8
}

private [util] object StringSerializerImpl {
  def serializeToString(c: Context)(serializable: c.Expr[Any], encoding: c.Expr[String]): c.Expr[String] = {
    import c.universe._
    val osVal = newTermName(c.fresh("os"))
    val a = q"""{
      val $osVal = new java.io.ByteArrayOutputStream()
      $serializable.serialize($osVal)
      $osVal.toString($encoding)
    }"""
    c.Expr(a)
  }

  def serializeToStringUtf8(c: Context)(serializable: c.Expr[Any]): c.Expr[String] = {
    import c.universe._
    serializeToString(c)( serializable, c.Expr(Literal(Constant("UTF-8"))))
  }
}
