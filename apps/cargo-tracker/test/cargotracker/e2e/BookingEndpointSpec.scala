package cargotracker.e2e

import cargotracker.shipper.domain.{Shipper, ShipperRepository}
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class BookingEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def seedShipper(repo: ShipperRepository): String =
    val id = repo.nextIdentity()
    val s = Shipper
      .individual(
        id,
        "予約用荷主",
        s"booking-${id.value}@example.com",
        "0",
        "addr"
      )
      .toOption
      .get
    repo.save(s)
    id.value

  "GET /bookings" should {
    "貨物予約一覧画面を表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/bookings")).get
        status(result) shouldBe OK
        contentAsString(result) should include("貨物予約一覧")
      }
    }
  }

  "GET /bookings/new" should {
    "貨物予約登録フォームを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/bookings/new")).get
        status(result) shouldBe OK
        contentAsString(result) should include("貨物予約登録")
        contentAsString(result) should include("危険物")
      }
    }
  }

  "POST /bookings" should {
    "既存荷主で予約すると詳細ページへリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val shipperCode = seedShipper(app.injector.instanceOf[ShipperRepository])
        val request = FakeRequest(POST, "/bookings")
          .withFormUrlEncodedBody(
            "shipperCode" -> shipperCode,
            "origin" -> "JPYOK",
            "destination" -> "USNYC",
            "arrivalDeadline" -> "2026-12-31",
            "cargoType" -> "General",
            "weightKg" -> "1000"
          )
          .withCSRFToken
        val result = route(app, request).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should startWith("/bookings/BK-")
        flash(result).get("success").get should include("BK-")
      }
    }

    "未知の荷主の予約は失敗する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val request = FakeRequest(POST, "/bookings")
          .withFormUrlEncodedBody(
            "shipperCode" -> "SH-999999",
            "origin" -> "JPYOK",
            "destination" -> "USNYC",
            "arrivalDeadline" -> "2026-12-31",
            "cargoType" -> "General",
            "weightKg" -> "1000"
          )
          .withCSRFToken
        val result = route(app, request).get
        status(result) shouldBe BAD_REQUEST
        contentAsString(result) should include("見つかりません")
      }
    }
  }
