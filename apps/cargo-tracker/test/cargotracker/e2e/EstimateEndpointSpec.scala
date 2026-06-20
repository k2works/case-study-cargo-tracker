package cargotracker.e2e

import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class EstimateEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  "GET /estimates" should {
    "見積一覧画面を表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/estimates")).get
        status(result) shouldBe OK
        contentAsString(result) should include("見積一覧")
      }
    }
  }

  "GET /estimates/new" should {
    "見積作成フォームを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/estimates/new")).get
        status(result) shouldBe OK
        contentAsString(result) should include("見積作成")
        contentAsString(result) should include("UnLocode")
      }
    }
  }

  "POST /estimates" should {
    "見積作成後に詳細ページへリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val request = FakeRequest(POST, "/estimates")
          .withFormUrlEncodedBody(
            "origin" -> "JPYOK",
            "destination" -> "USNYC",
            "deadline" -> "2026-12-31",
            "cargoType" -> "General",
            "weightKg" -> "100"
          )
          .withCSRFToken
        val result = route(app, request).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should startWith("/estimates/")
      }
    }

    "出発地と目的地が同一なら見積作成は失敗する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val request = FakeRequest(POST, "/estimates")
          .withFormUrlEncodedBody(
            "origin" -> "JPYOK",
            "destination" -> "JPYOK",
            "deadline" -> "2026-12-31",
            "cargoType" -> "General",
            "weightKg" -> "100"
          )
          .withCSRFToken
        val result = route(app, request).get
        status(result) shouldBe BAD_REQUEST
        contentAsString(result) should include("料金算出に失敗")
      }
    }
  }
