package cargotracker.e2e

import cargotracker.booking.application.commandservices.{BookCargoCommand, BookingCommandService}
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.support.PostgresContainerSupport
import cargotracker.tracking.application.commandservices.{AssignTrackingNumberCommand, TrackingCommandService}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import java.time.LocalDate

/** US18 追跡情報照会 E2E（IT5 タスク 3.4）。
  *
  *   - 公開 URL `/public/tracking/:trackingNumber` が未認証で参照できることを確認
  *   - 存在しない追跡番号は 404 を返す
  */
class PublicTrackingEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def seedShipper(repo: ShipperRepository): String =
    val id = repo.nextIdentity()
    repo.save(
      Shipper
        .individual(id, "公開追跡用荷主", s"public-tracking-${id.value}@example.com", "0", "addr")
        .toOption
        .get
    )
    id.value

  private def issueTracking(app: play.api.Application): String =
    val shipperCode = seedShipper(app.injector.instanceOf[ShipperRepository])
    val bookingService = app.injector.instanceOf[BookingCommandService]
    val trackingService = app.injector.instanceOf[TrackingCommandService]
    val bookingId = bookingService
      .book(
        BookCargoCommand(
          shipperCode = shipperCode,
          origin = "JPTYO",
          destination = "USLAX",
          arrivalDeadline = LocalDate.parse("2099-12-31"),
          cargoType = "General",
          weightKg = 1000,
          description = None,
          quantity = None,
          hazardousClass = None,
          hazardousUnNumber = None,
          hazardousProperName = None,
          refrigerationMinTemp = None,
          refrigerationMaxTemp = None,
          refrigerationUnit = None
        )
      )
      .toOption
      .get
      .bookingId
      .value
    bookingService.assignToRouting(bookingId).toOption.get
    bookingService.assignItinerary(bookingId, List("VY-1")).toOption.get
    bookingService.confirm(bookingId).toOption.get
    val ta = trackingService.assign(AssignTrackingNumberCommand(bookingId)).toOption.get
    bookingService.issueTracking(bookingId, ta.trackingNumber.value).toOption.get
    ta.trackingNumber.value

  "GET /public/tracking/:trackingNumber" should {

    "未認証で 200 を返し追跡番号と現在状態を含む（US18 ハッピーパス）" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val tn = issueTracking(app)
        val result = route(app, FakeRequest(GET, s"/public/tracking/$tn")).get
        status(result) shouldBe OK
        val body = contentAsString(result)
        body should include(tn)
        body should include("NotReceived")
      }
    }

    "存在しない追跡番号は 404 で「追跡番号が見つかりません」を表示" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/public/tracking/TN-999999")).get
        status(result) shouldBe NOT_FOUND
        contentAsString(result) should include("追跡番号が見つかりません")
      }
    }
  }

  "認証ユーザー向け /tracking/:trackingNumber は認証必須" in withContainers { container =>
    val app = buildApp(container)
    running(app) {
      val result = route(app, FakeRequest(GET, "/tracking/TN-000001")).get
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/login")
    }
  }
