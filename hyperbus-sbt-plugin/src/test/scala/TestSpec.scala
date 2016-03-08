import java.util

import com.mulesoft.raml.webpack.holders.JSConsole
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.mulesoft.raml1.java.parser.path.resolver.IJavaPathResolver
import eu.inn.hyperbus.raml.utils.JsToLogConsole
import eu.inn.hyperbus.raml.{GeneratorOptions, InterfaceGenerator}
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.JavaConversions
import scala.io.Source

class TestSpec extends FreeSpec with Matchers {
  "RAML" in {
    import JavaConversions._
    val factory = new JavaNodeFactory()

    val existingConsole = factory.getBindings.get("console").asInstanceOf[JSConsole]
    factory.getBindings.put("console", new JsToLogConsole(existingConsole.engine))

    factory.setPathResolver(new IJavaPathResolver {
      override def list(path: String): util.List[String] = List("revault.raml")
      override def content(path: String): String = {
        val source = Source.fromURL(getClass.getResource(path))
        source.getLines().mkString("\n")
      }
    })

    val api = factory.createApi("revault.raml")
    api.getErrors.foreach(s ⇒ println(s"---> $s"))

    val gen = new InterfaceGenerator(api, GeneratorOptions(packageName = "eu.inn.protocol"))
    val result = gen.generate()
    println(result)
    result.length should be > 0
  }
}
