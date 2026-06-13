package cargotracker.e2e

import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class HealthEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  "GET /health" should {
    "DB 疎通込みで UP を返す" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/health")).get

        status(result) shouldBe OK
        (contentAsJson(result) \ "status").as[String] shouldBe "UP"
      }
    }
  }

  "GET /" should {
    "ホーム画面を表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/")).get

        status(result) shouldBe OK
        contentAsString(result) should include("国際貨物輸送管理システム")
      }
    }
  }
