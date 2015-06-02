import com.typesafe.config.ConfigFactory
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport._
import eu.inn.servicebus.{ServiceBus, ServiceBusConfiguration, ServiceBusConfigurationLoader, TransportRoute}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ServiceBusConfigurationTest extends FreeSpec with ScalaFutures with Matchers {
  "ServiceBus " - {
    "Configuration Test " in {
      val config = ConfigFactory.parseString("""
        client-routes: [
          {
            url: "/topic/{userId}", match-type: Exact
            partition-args: { userId: { value: null, match-type: Any } },
            class-name: InprocTransport,
            configuration: null
          }
        ],

        server-routes: [
          {
            url: "/topic/{userId}", match-type: Exact
            partition-args: { userId: { value: ".*", match-type: Regex } },
            class-name: InprocTransport,
            configuration: null
          }
        ]
      """)

      val sbc = ServiceBusConfigurationLoader.fromConfig(config)

      assert(sbc.clientRoutes.nonEmpty)
      sbc.clientRoutes.head.urlArg should equal(ExactArg("/topic/{userId}"))
      sbc.clientRoutes.head.partitionArgs should equal(PartitionArgs(Map(
        "userId" → AnyArg
      )))
      sbc.clientRoutes.head.transport shouldBe a [InprocTransport]

      assert(sbc.serverRoutes.nonEmpty)
      sbc.serverRoutes.head.urlArg should equal(ExactArg("/topic/{userId}"))
      sbc.serverRoutes.head.partitionArgs should equal(PartitionArgs(Map(
        "userId" → RegexArg(".*")
      )))
      sbc.serverRoutes.head.transport shouldBe a [InprocTransport]
    }
  }
}
