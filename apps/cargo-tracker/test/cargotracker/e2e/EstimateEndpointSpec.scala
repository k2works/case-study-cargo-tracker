package cargotracker.e2e

import cargotracker.support.AuthenticatedRequestSupport.*
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
        val result = route(app, FakeRequest(GET, "/estimates").withAuthenticatedSession).get
        status(result) shouldBe OK
        contentAsString(result) should include("見積一覧")
      }
    }

    // データを 1 件以上 seed して一覧を取得する回帰テスト。
    // findAll() は内部で findById をループ呼び出しするため、空テーブル時には
    // SELECT 句のカラム漏れ（例: version）に気付けない。1 件作成してから一覧を
    // 引くことで Repository の SELECT 句が実テーブルと整合しているかを保証する。
    "見積を 1 件作成した後に一覧画面が 500 にならない（SELECT 句カラム整合性の回帰）" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val create = FakeRequest(POST, "/estimates")
          .withFormUrlEncodedBody(
            "origin" -> "JPYOK",
            "destination" -> "USNYC",
            "deadline" -> "2026-12-31",
            "cargoType" -> "General",
            "weightKg" -> "100"
          )
          .withAuthenticatedSession
          .withCSRFToken
        status(route(app, create).get) shouldBe SEE_OTHER

        val list = route(app, FakeRequest(GET, "/estimates").withAuthenticatedSession).get
        status(list) shouldBe OK
        contentAsString(list) should include("見積一覧")
      }
    }
  }

  "GET /estimates/new" should {
    "見積作成フォームを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/estimates/new").withAuthenticatedSession).get
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
          .withAuthenticatedSession
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
          .withAuthenticatedSession
          .withCSRFToken
        val result = route(app, request).get
        status(result) shouldBe BAD_REQUEST
        contentAsString(result) should include("料金算出に失敗")
      }
    }
  }
