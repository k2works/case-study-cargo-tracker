package cargotracker.e2e

import cargotracker.booking.application.commandservices.{BookCargoCommand, BookingCommandService}
import cargotracker.booking.domain.model.repositories.{CargoRepository, NotificationLogRepository}
import cargotracker.booking.domain.model.valueobjects.{BookingId, NotificationType}
import cargotracker.handling.domain.model.repositories.HandlingActivityRepository
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.support.AuthenticatedRequestSupport.*
import cargotracker.support.PostgresContainerSupport
import cargotracker.tracking.application.commandservices.{AssignTrackingNumberCommand, TrackingCommandService}
import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.TrackingBookingId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import java.time.LocalDate

/** US15 荷役作業記録 E2E（IT5 タスク 2.10）。 */
class HandlingFlowEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def seedShipper(repo: ShipperRepository): String =
    val id = repo.nextIdentity()
    repo.save(
      Shipper
        .individual(id, "荷役用荷主", s"handling-${id.value}@example.com", "0", "addr")
        .toOption
        .get
    )
    id.value

  private def bootedWithTracking(app: play.api.Application): (String, String) =
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
    (bookingId, ta.trackingNumber.value)

  private def postHandling(
      tn: String,
      eventType: String,
      completionDateTime: String,
      location: String,
      voyageNumber: String = "",
      operatorName: String = ""
  ) =
    FakeRequest(POST, "/handling")
      .withFormUrlEncodedBody(
        "trackingNumber" -> tn,
        "eventType" -> eventType,
        "completionDateTime" -> completionDateTime,
        "locationUnLocode" -> location,
        "voyageNumber" -> voyageNumber,
        "operatorName" -> operatorName
      )
      .withAuthenticatedSession
      .withCSRFToken

  "POST /handling" should {

    "Receive 作業を登録すると TrackingActivity 状態が Received に更新され通知ログが記録される（US15 ハッピーパス）" in
      withContainers { container =>
        val app = buildApp(container)
        running(app) {
          val (bookingId, tn) = bootedWithTracking(app)
          val result =
            route(app, postHandling(tn, "Receive", "2026-08-17T10:00:00", "JPTYO", operatorName = "山田太郎")).get
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/handling")
          flash(result).get("success").get should include(tn)

          val handlings = app.injector.instanceOf[HandlingActivityRepository].findByBookingId(bookingId)
          handlings should have size 1
          handlings.head.eventType.toString shouldBe "Receive"
          handlings.head.operatorName shouldBe Some("山田太郎")

          val ta = app.injector
            .instanceOf[TrackingActivityRepository]
            .findByBookingId(TrackingBookingId.unsafeFrom(bookingId))
            .get
          ta.transportStatus shouldBe TrackingStatus.Received
          ta.events should have size 1
          ta.events.head.eventType shouldBe "Receive"

          val cargo = app.injector
            .instanceOf[CargoRepository]
            .findById(BookingId.unsafeFrom(bookingId))
            .get
          val logs = app.injector.instanceOf[NotificationLogRepository].findByBookingId(cargo.bookingId)
          logs.map(_.notificationType) should contain(NotificationType.HandlingRecorded)
        }
      }

    "Receive → Load を順次登録すると TrackingStatus が Received → Loaded と遷移する（US15 状態自動更新）" in
      withContainers { container =>
        val app = buildApp(container)
        running(app) {
          val (_, tn) = bootedWithTracking(app)
          val first = route(app, postHandling(tn, "Receive", "2026-08-17T10:00:00", "JPTYO")).get
          status(first) shouldBe SEE_OTHER // Receive の commit を await
          val second =
            route(app, postHandling(tn, "Load", "2026-08-17T14:00:00", "JPTYO", voyageNumber = "VY-1")).get
          status(second) shouldBe SEE_OTHER

          val ta = app.injector
            .instanceOf[TrackingActivityRepository]
            .findByTrackingNumber(
              cargotracker.tracking.domain.model.valueobjects.TrackingNumber.unsafeFrom(tn)
            )
            .get
          ta.transportStatus shouldBe TrackingStatus.Loaded
          ta.events should have size 2
        }
      }

    "存在しない追跡番号は error フラッシュ" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, postHandling("TN-999999", "Receive", "2026-08-17T10:00:00", "JPTYO")).get
        status(result) shouldBe SEE_OTHER
        flash(result).get("error").get should include("見つかりません")
      }
    }
  }
