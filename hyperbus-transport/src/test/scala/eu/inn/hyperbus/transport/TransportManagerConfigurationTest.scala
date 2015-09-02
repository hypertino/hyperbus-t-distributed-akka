package eu.inn.hyperbus.transport

import com.typesafe.config.{Config, ConfigFactory}
import eu.inn.hyperbus.transport.api._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MockClientTransport(config: Config) extends ClientTransport {
  override def ask[OUT <: TransportResponse](message: TransportRequest, outputDecoder: Decoder[OUT]): Future[OUT] = ???
  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???
  override def publish(message: TransportRequest): Future[PublishResult] = ???
}

class MockServerTransport(config: Config) extends ServerTransport {
  override def process[IN <: TransportRequest](topicFilter: Topic, inputDecoder: Decoder[IN], exceptionEncoder: Encoder[Throwable])(handler: (IN) ⇒ Future[TransportResponse]): String = ???
  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???
  override def subscribe[IN <: TransportRequest](topicFilter: Topic, groupName: String, inputDecoder: Decoder[IN])(handler: (IN) ⇒ Future[Unit]): String = ???
  override def off(subscriptionId: String): Unit = ???
}

class TransportManagerConfigurationTest extends FreeSpec with ScalaFutures with Matchers {
  "Transport Manager" - {
    "Configuration Test " in {
      val config = ConfigFactory.parseString("""
        service-bus: {
          transports: {
            mock-client.class-name: eu.inn.hyperbus.transport.MockClientTransport,
            mock-server.class-name: eu.inn.hyperbus.transport.MockServerTransport,
          },
          client-routes: [
            {
              url: "/topic/{userId}", match-type: Exact
              partition-args: { userId: { value: null, match-type: Any } },
              transport: mock-client
            }
          ],
          server-routes: [
            {
              url: "/topic/{userId}", match-type: Exact
              partition-args: { userId: { value: ".*", match-type: Regex } },
              transport: mock-server
            }
          ]
        }
      """)

      val sbc = TransportConfigurationLoader.fromConfig(config)

      assert(sbc.clientRoutes.nonEmpty)
      sbc.clientRoutes.head.urlArg should equal(SpecificValue("/topic/{userId}"))
      sbc.clientRoutes.head.valueFilters should equal(Filters(Map(
        "userId" → AnyValue
      )))
      sbc.clientRoutes.head.transport shouldBe a [MockClientTransport]

      assert(sbc.serverRoutes.nonEmpty)
      sbc.serverRoutes.head.urlArg should equal(SpecificValue("/topic/{userId}"))
      sbc.serverRoutes.head.valueFilters should equal(Filters(Map(
        "userId" → RegexFilter(".*")
      )))
      sbc.serverRoutes.head.transport shouldBe a [MockServerTransport]
    }
  }
}
