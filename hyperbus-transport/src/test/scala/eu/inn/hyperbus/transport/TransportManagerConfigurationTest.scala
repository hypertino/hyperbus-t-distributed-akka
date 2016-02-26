package eu.inn.hyperbus.transport

import com.typesafe.config.{Config, ConfigFactory}
import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.{AnyValue, RegexTextMatcher, SpecificValue, TransportRequestMatcher}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MockClientTransport(config: Config) extends ClientTransport {
  override def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse] = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???

  override def publish(message: TransportRequest): Future[PublishResult] = ???
}

class MockServerTransport(config: Config) extends ServerTransport {
  override def onCommand(requestMatcher: TransportRequestMatcher,
                         inputDeserializer: RequestDeserializer[Request[Body]])
                        (handler: (Request[Body]) => Future[TransportResponse]): Future[Subscription] = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???

  override def onEvent(requestMatcher: TransportRequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[Request[Body]])
                      (handler: (Request[Body]) => Future[Unit]): Future[Subscription] = ???

  override def off(subscription: Subscription): Future[Unit] = ???
}

class TransportManagerConfigurationTest extends FreeSpec with ScalaFutures with Matchers {
  "Transport Manager" - {
    "Configuration Test " in {
      val config = ConfigFactory.parseString(
        """
        hyperbus: {
          transports: {
            mock-client.class-name: eu.inn.hyperbus.transport.MockClientTransport,
            mock-server.class-name: eu.inn.hyperbus.transport.MockServerTransport,
          },
          client-routes: [
            {
              match: {
                uri: {
                  pattern: { value: "/topic/{userId}", match-type: Specific }
                  args: { userId: { match-type: Any } }
                }
                headers: {
                  method: { value: "post" }
                }
              }
              transport: mock-client
            }
          ],
          server-routes: [
            {
              match: {
                uri: {
                  pattern: { value: "/topic/{userId}", match-type: Specific }
                  args: { userId: { value: ".*", match-type: Regex } }
                }
              }
              transport: mock-server
            }
          ]
        }
        """)

      val sbc = TransportConfigurationLoader.fromConfig(config)

      assert(sbc.clientRoutes.nonEmpty)
      assert(sbc.clientRoutes.head.matcher.uri.nonEmpty)
      sbc.clientRoutes.head.matcher.uri.get.pattern should equal(SpecificValue("/topic/{userId}"))
      sbc.clientRoutes.head.matcher.uri.get.args should equal(Map(
        "userId" → AnyValue
      ))
      sbc.clientRoutes.head.matcher.headers should equal(Map(
        "method" → SpecificValue("post")
      ))
      sbc.clientRoutes.head.transport shouldBe a[MockClientTransport]

      assert(sbc.serverRoutes.nonEmpty)
      assert(sbc.serverRoutes.head.matcher.uri.nonEmpty)
      sbc.serverRoutes.head.matcher.uri.get.pattern should equal(SpecificValue("/topic/{userId}"))
      sbc.serverRoutes.head.matcher.uri.get.args should equal(Map(
        "userId" → RegexTextMatcher(".*")
      ))
      sbc.serverRoutes.head.transport shouldBe a[MockServerTransport]
    }
  }
}
