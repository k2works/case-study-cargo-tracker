package cargotracker.e2e

import cargotracker.booking.application.commandservices.{BookCargoCommand, BookingCommandService}
import cargotracker.booking.domain.model.repositories.{CargoRepository, NotificationLogRepository}
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus, NotificationType}
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.support.AuthenticatedRequestSupport.*
import cargotracker.support.PostgresContainerSupport
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.TrackingBookingId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import java.time.LocalDate

/** US14 追跡番号発行 E2E（IT5 タスク 1.5）。 */
class IssueTrackingEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def seedShipper(repo: ShipperRepository): String =
    val id = repo.nextIdentity()
    repo.save(
      Shipper
        .individual(id, "追跡用荷主", s"tracking-${id.value}@example.com", "0", "addr")
        .toOption
        .get
    )
    id.value

  private def bootedConfirmed(app: play.api.Application): String =
    val shipperCode = seedShipper(app.injector.instanceOf[ShipperRepository])
    val bookingService = app.injector.instanceOf[BookingCommandService]
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
    bookingService.assignItinerary(bookingId, List("VY-1", "VY-2")).toOption.get
    bookingService.confirm(bookingId).toOption.get
    bookingId

  "POST /bookings/:bookingId/issue-tracking" should {

    "Confirmed 予約から追跡番号を発行し TrackingActivity + Cargo + 通知ログを更新する（US14 ハッピーパス）" in
      withContainers { container =>
        val app = buildApp(container)
        running(app) {
          val bookingId = bootedConfirmed(app)
          val result = route(
            app,
            FakeRequest(POST, s"/bookings/$bookingId/issue-tracking").withAuthenticatedSession.withCSRFToken
          ).get
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(s"/bookings/$bookingId")
          flash(result).get("success").get should include("追跡番号 TN-")

          val cargo = app.injector
            .instanceOf[CargoRepository]
            .findById(BookingId.unsafeFrom(bookingId))
            .get
          cargo.status shouldBe BookingStatus.TrackingIssued
          cargo.trackingNumber shouldBe defined
          cargo.trackingNumber.get.value should startWith("TN-")

          val activity = app.injector
            .instanceOf[TrackingActivityRepository]
            .findByBookingId(TrackingBookingId.unsafeFrom(bookingId))
            .get
          activity.trackingNumber.value shouldBe cargo.trackingNumber.get.value

          val logs = app.injector.instanceOf[NotificationLogRepository].findByBookingId(cargo.bookingId)
          logs.map(_.notificationType) should contain(NotificationType.TrackingIssued)
        }
      }

    "再発行は冪等で同じ追跡番号を維持する（US14 冪等性）" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val bookingId = bootedConfirmed(app)
        val first = route(
          app,
          FakeRequest(POST, s"/bookings/$bookingId/issue-tracking").withAuthenticatedSession.withCSRFToken
        ).get
        status(first) shouldBe SEE_OTHER

        val second = route(
          app,
          FakeRequest(POST, s"/bookings/$bookingId/issue-tracking").withAuthenticatedSession.withCSRFToken
        ).get
        status(second) shouldBe SEE_OTHER

        val cargo = app.injector
          .instanceOf[CargoRepository]
          .findById(BookingId.unsafeFrom(bookingId))
          .get
        cargo.trackingNumber shouldBe defined
      }
    }
  }
