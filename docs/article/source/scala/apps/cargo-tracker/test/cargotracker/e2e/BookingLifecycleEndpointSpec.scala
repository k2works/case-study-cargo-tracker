package cargotracker.e2e

import cargotracker.booking.application.commandservices.{BookCargoCommand, BookingCommandService}
import cargotracker.booking.domain.model.repositories.{CargoRepository, NotificationLogRepository}
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus, NotificationType}
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.support.AuthenticatedRequestSupport.*
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import java.time.LocalDate

/** US13 予約確定・再設計・キャンセルの E2E（IT4 タスク 4.5）。 */
class BookingLifecycleEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def seedShipper(repo: ShipperRepository): String =
    val id = repo.nextIdentity()
    repo.save(
      Shipper
        .individual(id, "ライフサイクル荷主", s"lifecycle-${id.value}@example.com", "0", "addr")
        .toOption
        .get
    )
    id.value

  private def bootedRouteAssigned(app: play.api.Application): String =
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
    bookingService.assignToRouting(bookingId)
    bookingService.assignItinerary(bookingId, List("VY-1", "VY-2"))
    bookingId

  "POST /bookings/:bookingId/confirm" should {
    "RouteAssigned 予約を確定し BookingConfirmed 通知を記録する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val bookingId = bootedRouteAssigned(app)
        val result = route(
          app,
          FakeRequest(POST, s"/bookings/$bookingId/confirm").withAuthenticatedSession.withCSRFToken
        ).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bookings/$bookingId")
        flash(result).get("success").get should include("予約を確定しました")

        val cargo = app.injector.instanceOf[CargoRepository].findById(BookingId.unsafeFrom(bookingId)).get
        cargo.status shouldBe BookingStatus.Confirmed

        val logs = app.injector.instanceOf[NotificationLogRepository].findByBookingId(cargo.bookingId)
        logs.map(_.notificationType) should contain(NotificationType.BookingConfirmed)
      }
    }
  }

  "POST /bookings/:bookingId/repropose-route" should {
    "RouteAssigned 予約を再設計に戻し itinerary がリセットされる" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val bookingId = bootedRouteAssigned(app)
        val result = route(
          app,
          FakeRequest(POST, s"/bookings/$bookingId/repropose-route").withAuthenticatedSession.withCSRFToken
        ).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bookings/$bookingId")
        flash(result).get("success").get should include("経路設計者")

        val cargo = app.injector.instanceOf[CargoRepository].findById(BookingId.unsafeFrom(bookingId)).get
        cargo.status shouldBe BookingStatus.RouteProposed
        cargo.itinerary shouldBe None
      }
    }
  }

  "POST /bookings/:bookingId/cancel" should {
    "RouteAssigned 予約をキャンセルし BookingCancelled 通知を記録する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val bookingId = bootedRouteAssigned(app)
        val result = route(
          app,
          FakeRequest(POST, s"/bookings/$bookingId/cancel").withAuthenticatedSession.withCSRFToken
        ).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bookings/$bookingId")
        flash(result).get("success").get should include("キャンセルしました")

        val cargo = app.injector.instanceOf[CargoRepository].findById(BookingId.unsafeFrom(bookingId)).get
        cargo.status shouldBe BookingStatus.Cancelled

        val logs = app.injector.instanceOf[NotificationLogRepository].findByBookingId(cargo.bookingId)
        logs.map(_.notificationType) should contain(NotificationType.BookingCancelled)
      }
    }

    "Cancelled 予約への再キャンセルは error フラッシュ" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val bookingId = bootedRouteAssigned(app)
        val bookingService = app.injector.instanceOf[BookingCommandService]
        bookingService.cancel(bookingId)

        val result = route(
          app,
          FakeRequest(POST, s"/bookings/$bookingId/cancel").withAuthenticatedSession.withCSRFToken
        ).get
        status(result) shouldBe SEE_OTHER
        flash(result).get("error").get should include("Cancelled")
      }
    }
  }
