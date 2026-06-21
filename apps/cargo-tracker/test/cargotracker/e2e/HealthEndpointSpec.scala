package cargotracker.e2e

import cargotracker.support.AuthenticatedRequestSupport.*
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
    "ロール別ダッシュボードを表示する（要認証）" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result =
          route(app, FakeRequest(GET, "/").withAuthenticatedSession).get

        status(result) shouldBe OK
        val body = contentAsString(result)
        body should include("ダッシュボード")
        body should include("ログイン中")
      }
    }

    "未認証時はログイン画面にリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/")).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/login")
      }
    }
  }
