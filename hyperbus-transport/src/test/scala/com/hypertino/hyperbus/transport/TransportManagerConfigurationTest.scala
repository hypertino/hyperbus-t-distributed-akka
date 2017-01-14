package com.hypertino.hyperbus.transport

import com.typesafe.config.{Config, ConfigFactory}
import com.hypertino.hyperbus.model.{Body, Request}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.{Any, RegexMatcher, RequestMatcher, Specific}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import rx.lang.scala.Observer

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MockClientTransport(config: Config) extends ClientTransport {
  override def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse] = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???

  override def publish(message: TransportRequest): Future[PublishResult] = ???
}

class MockServerTransport(config: Config) extends ServerTransport {
  override def onCommand[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                         inputDeserializer: RequestDeserializer[REQ])
                        (handler: (REQ) => Future[TransportResponse]): Future[Subscription] = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???

  override def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[REQ],
                       subscriber: Observer[REQ]): Future[Subscription] = ???

  override def off(subscription: Subscription): Future[Unit] = ???
}

class TransportManagerConfigurationTest extends FreeSpec with ScalaFutures with Matchers {
  "Transport Manager" - {
    "Configuration Test" in {
      val config = ConfigFactory.parseString(
        """
        hyperbus: {
          transports: {
            mock-client.class-name: com.hypertino.hyperbus.transport.MockClientTransport,
            mock-server.class-name: com.hypertino.hyperbus.transport.MockServerTransport,
          },
          client-routes: [
            {
              match: {
                uri: {
                  pattern: { value: "/topic/{userId}", type: Specific }
                  args: { userId: { type: Any } }
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
                  pattern: { value: "/topic/{userId}", type: Specific }
                  args: { userId: { value: ".*", type: Regex } }
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
      sbc.clientRoutes.head.matcher.uri.get.pattern should equal(Specific("/topic/{userId}"))
      sbc.clientRoutes.head.matcher.uri.get.args should equal(Map(
        "userId" → Any
      ))
      sbc.clientRoutes.head.matcher.headers should equal(Map(
        "method" → Specific("post")
      ))
      sbc.clientRoutes.head.transport shouldBe a[MockClientTransport]

      assert(sbc.serverRoutes.nonEmpty)
      assert(sbc.serverRoutes.head.matcher.uri.nonEmpty)
      sbc.serverRoutes.head.matcher.uri.get.pattern should equal(Specific("/topic/{userId}"))
      sbc.serverRoutes.head.matcher.uri.get.args should equal(Map(
        "userId" → RegexMatcher(".*")
      ))
      sbc.serverRoutes.head.transport shouldBe a[MockServerTransport]
    }
  }
}
