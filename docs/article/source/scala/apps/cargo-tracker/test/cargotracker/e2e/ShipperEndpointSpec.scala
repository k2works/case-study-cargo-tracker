package cargotracker.e2e

import cargotracker.support.AuthenticatedRequestSupport.*
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class ShipperEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  "GET /shippers" should {
    "荷主一覧画面を表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/shippers").withAuthenticatedSession).get
        status(result) shouldBe OK
        contentAsString(result) should include("荷主一覧")
      }
    }
  }

  "GET /shippers/new" should {
    "荷主登録フォームを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/shippers/new").withAuthenticatedSession).get
        status(result) shouldBe OK
        contentAsString(result) should include("荷主登録")
        contentAsString(result) should include("個人")
        contentAsString(result) should include("法人")
      }
    }
  }

  "POST /shippers" should {
    "個人荷主を登録すると一覧にリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val request = FakeRequest(POST, "/shippers")
          .withFormUrlEncodedBody(
            "name" -> "山田太郎",
            "email" -> "yamada-e2e@example.com",
            "phone" -> "03-1111-1111",
            "address" -> "東京都",
            "shipperType" -> "Individual"
          )
          .withAuthenticatedSession
          .withCSRFToken
        val result = route(app, request).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/shippers")
        flash(result).get("success").get should include("SH-")
      }
    }

    "法人荷主を割引率付きで登録できる" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val request = FakeRequest(POST, "/shippers")
          .withFormUrlEncodedBody(
            "name" -> "株式会社 ABC",
            "email" -> "abc-e2e@example.com",
            "phone" -> "03-2222-2222",
            "address" -> "東京都港区",
            "shipperType" -> "Corporate",
            "contractNumber" -> "CT-E2E-0001",
            "discountRate" -> "0.15"
          )
          .withAuthenticatedSession
          .withCSRFToken
        val result = route(app, request).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/shippers")
      }
    }
  }

  "GET /shippers/check-email" should {
    "未登録メールでは空のレスポンスを返す" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(
          app,
          FakeRequest(GET, "/shippers/check-email?email=novel@example.com").withAuthenticatedSession
        ).get
        status(result) shouldBe OK
        contentAsString(result) shouldBe ""
      }
    }
  }
