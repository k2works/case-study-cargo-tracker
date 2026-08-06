package cargotracker.e2e

import cargotracker.support.AuthenticatedRequestSupport.*
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers.*

/** VoyageController の統合テスト（IT3 タスク 0.1）。
  *
  * Play `FakeRequest` + Testcontainers PostgreSQL で Routing コンテキストの 全 5 エンドポイントを通す。
  */
class VoyageEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def voyagePayload(voyageNumber: String): Seq[(String, String)] = Seq(
    "voyageNumber" -> voyageNumber,
    "movements[0].departureLocation" -> "JPTYO",
    "movements[0].arrivalLocation" -> "USLAX",
    "movements[0].departureTime" -> "2026-08-01T10:00",
    "movements[0].arrivalTime" -> "2026-08-15T18:00"
  )

  "GET /voyages" should {
    "認証済みで航海一覧を表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result =
          route(app, FakeRequest(GET, "/voyages").withAuthenticatedSession).get

        status(result) shouldBe OK
        contentAsString(result) should include("航路一覧")
      }
    }

    "未認証時はログイン画面にリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/voyages")).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/login")
      }
    }
  }

  "GET /voyages/new" should {
    "航海登録フォームを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result =
          route(app, FakeRequest(GET, "/voyages/new").withAuthenticatedSession).get
        status(result) shouldBe OK
        contentAsString(result) should include("航海スケジュール登録")
      }
    }
  }

  "POST /voyages" should {
    "正常な入力で航海を登録すると一覧へリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val csrf = "test-csrf-token"
        val req = FakeRequest(POST, "/voyages")
          .withFormUrlEncodedBody(voyagePayload("VY-INT01") :+ ("csrfToken" -> csrf): _*)
          .withSession("csrfToken" -> csrf)
          .withAuthenticatedSession
          .withHeaders("Csrf-Token" -> csrf)
        val result = route(app, req).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/voyages")
        flash(result).get("success") should not be empty
      }
    }

    // 重複登録の検証は Twirl の @CSRF.formField 再描画が必要なため E2E（Playwright）側で
    // カバー（us24-25-voyage.spec.ts L33）。Endpoint レベルでは初回 SEE_OTHER の検証で十分。

    // 航海を 1 件登録した後に一覧画面が 500 にならない回帰テスト。
    // findAll は SELECT id, voyage_number, version, ... と列を明示しているため
    // 楽観ロック等で列を追加した際に取り忘れると ResultSet 列名不在で 500 になる。
    // 空テーブル時は SELECT すら走らず気付けないので 1 件 seed 後に GET する。
    "航海を 1 件登録した後に /voyages 一覧画面が 500 にならない（SELECT 句カラム整合性の回帰）" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val csrf = "test-csrf-token"
        val create = FakeRequest(POST, "/voyages")
          .withFormUrlEncodedBody(voyagePayload("VY-REG01") :+ ("csrfToken" -> csrf): _*)
          .withSession("csrfToken" -> csrf)
          .withAuthenticatedSession
          .withHeaders("Csrf-Token" -> csrf)
        status(route(app, create).get) shouldBe SEE_OTHER

        val list = route(app, FakeRequest(GET, "/voyages").withAuthenticatedSession).get
        status(list) shouldBe OK
        contentAsString(list) should include("VY-REG01")
      }
    }
  }

  "GET /voyages/:voyageNumber/edit" should {
    "未登録航海番号で NotFound を返す" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result =
          route(
            app,
            FakeRequest(GET, "/voyages/VY-UNKNOWN/edit").withAuthenticatedSession
          ).get
        status(result) shouldBe NOT_FOUND
      }
    }
  }

  // === US07 航海スケジュール検索（IT3 タスク 1.6） ===

  "GET /voyages/search" should {
    "クエリ未指定なら検索フォームのみを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/voyages/search").withAuthenticatedSession).get
        status(result) shouldBe OK
        contentAsString(result) should include("航海スケジュール検索")
      }
    }

    "出発港の UnLocode 形式不正は BadRequest" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result =
          route(app, FakeRequest(GET, "/voyages/search?origin=xx").withAuthenticatedSession).get
        status(result) shouldBe BAD_REQUEST
        contentAsString(result) should include("出発港")
      }
    }

    "貨物種別が未知の名称は BadRequest" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result =
          route(
            app,
            FakeRequest(GET, "/voyages/search?cargoType=Unknown").withAuthenticatedSession
          ).get
        status(result) shouldBe BAD_REQUEST
        contentAsString(result) should include("貨物種別")
      }
    }

    "未認証時はログイン画面にリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/voyages/search")).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/login")
      }
    }
  }
