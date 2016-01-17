package eu.inn.hyperbus.transport

import com.typesafe.config.{Config, ConfigFactory}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.uri._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MockClientTransport(config: Config) extends ClientTransport {
  override def ask[OUT <: TransportResponse](message: TransportRequest, outputDeserializer: Deserializer[OUT]): Future[OUT] = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???

  override def publish(message: TransportRequest): Future[PublishResult] = ???
}

class MockServerTransport(config: Config) extends ServerTransport {
  override def process[IN <: TransportRequest](uriFilter: Uri, inputDeserializer: Deserializer[IN], exceptionSerializer: Serializer[Throwable])(handler: (IN) ⇒ Future[TransportResponse]): String = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???

  override def subscribe[IN <: TransportRequest](uriFilter: Uri, groupName: String, inputDeserializer: Deserializer[IN])(handler: (IN) ⇒ Future[Unit]): String = ???

  override def off(subscriptionId: String): Unit = ???
}

class TransportManagerConfigurationTest extends FreeSpec with ScalaFutures with Matchers {
  "Transport Manager" - {
    "Configuration Test " in {
      val config = ConfigFactory.parseString("""
        hyperbus: {
          transports: {
            mock-client.class-name: eu.inn.hyperbus.transport.MockClientTransport,
            mock-server.class-name: eu.inn.hyperbus.transport.MockServerTransport,
          },
          client-routes: [
            {
              uri-filter: {
                pattern: { value: "/topic/{userId}", match-type: Specific }
                args: { userId: { match-type: Any } }
              }
              transport: mock-client
            }
          ],
          server-routes: [
            {
              uri-filter: {
                pattern: { value: "/topic/{userId}", match-type: Specific }
                args: { userId: { value: ".*", match-type: Regex } }
              }
              transport: mock-server
            }
          ]
        }
      """)

      val sbc = TransportConfigurationLoader.fromConfig(config)

      assert(sbc.clientRoutes.nonEmpty)
      sbc.clientRoutes.head.uri.pattern should equal(SpecificValue("/topic/{userId}"))
      sbc.clientRoutes.head.uri.args should equal(Map(
        "userId" → AnyValue
      ))
      sbc.clientRoutes.head.transport shouldBe a[MockClientTransport]

      assert(sbc.serverRoutes.nonEmpty)
      sbc.serverRoutes.head.uri.pattern should equal(SpecificValue("/topic/{userId}"))
      sbc.serverRoutes.head.uri.args should equal(Map(
        "userId" → RegexUriPart(".*")
      ))
      sbc.serverRoutes.head.transport shouldBe a[MockServerTransport]
    }
  }
}
