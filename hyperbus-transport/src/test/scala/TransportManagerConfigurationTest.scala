import com.typesafe.config.ConfigFactory
import eu.inn.servicebus.transport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

class TransportManagerConfigurationTest extends FreeSpec with ScalaFutures with Matchers {
  "ServiceBus " - {
    "Configuration Test " in {
      val config = ConfigFactory.parseString("""
        service-bus: {
          transports: {
            inproc: {
              class-name: InprocTransport,
              configuration: null
            }
          },
          client-routes: [
            {
              url: "/topic/{userId}", match-type: Exact
              partition-args: { userId: { value: null, match-type: Any } },
              transport: inproc
            }
          ],
          server-routes: [
            {
              url: "/topic/{userId}", match-type: Exact
              partition-args: { userId: { value: ".*", match-type: Regex } },
              transport: inproc
            }
          ]
        }
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
