import java.util
import javax.script.ScriptEngine

import com.mulesoft.raml.webpack.holders.{JSConsole, AbstractJSWrapper}
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import com.mulesoft.raml1.java.parser.model.datamodel.DataElement
import com.mulesoft.raml1.java.parser.path.resolver.IJavaPathResolver
import org.scalatest.{Matchers, FreeSpec}
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions
import scala.io.Source

class JsToLogConsole(engine: ScriptEngine) extends AbstractJSWrapper(engine) {
  val log = LoggerFactory.getLogger(getClass)
  def log(text: String) {
    log.debug(text)
  }

  def warn(text: String) {
    log.warn(text)
  }

  def getClassName: String = {
    "Console"
  }
}


class TestSpec extends FreeSpec with Matchers {
  "RAML" in {
    import JavaConversions._
    val factory = new JavaNodeFactory()

    val existingConsole = factory.getBindings.get("console").asInstanceOf[JSConsole]
    factory.getBindings.put("console", new JsToLogConsole(existingConsole.engine))

    factory.setPathResolver(new IJavaPathResolver {
      override def list(path: String): util.List[String] = List("test.raml")
      override def content(path: String): String = {
        val source = Source.fromURL(getClass.getResource(path))
        source.getLines().mkString("\n")
      }
    })

    val api = factory.createApi("test.raml")
    api.getErrors.foreach(s ⇒ println(s"---> $s"))


    println (" --> types: ")
    api.types.foreach { typ: DataElement ⇒
      println(typ.name)
      typ.facets()
      typ.getClass
    }

    api.resources.foreach{r ⇒ println(r.relativeUri.value)
      r.uriParameters.foreach { up ⇒
        println(up.name)
        println(up.leftSide)
        println(up.leftSideValue)
        println(up.getClass)
      }
    }


    "success" should equal("success")
  }
}
