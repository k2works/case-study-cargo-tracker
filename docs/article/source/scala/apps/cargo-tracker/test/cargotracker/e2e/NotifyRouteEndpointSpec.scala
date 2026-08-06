package cargotracker.e2e

import cargotracker.booking.application.commandservices.{BookCargoCommand, BookingCommandService}
import cargotracker.routing.application.commandservices.{RoutingCommandService, SelectRouteCommand}
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

/** US12 経路通知の E2E（IT4 タスク 3.4）。 */
class NotifyRouteEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def seedShipper(repo: ShipperRepository): String =
    val id = repo.nextIdentity()
    repo.save(
      Shipper
        .individual(id, "通知用荷主", s"notify-${id.value}@example.com", "0", "addr")
        .toOption
        .get
    )
    id.value

  "POST /bookings/:bookingId/notify-route" should {

    "RouteAssigned 予約に対し通知を発行して /notifications にリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
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
        val routingService = app.injector.instanceOf[RoutingCommandService]
        routingService.confirmRoute(SelectRouteCommand(bookingId, List("VY-PRESET")))
        bookingService.assignItinerary(bookingId, List("VY-PRESET"))

        val result = route(
          app,
          FakeRequest(POST, s"/bookings/$bookingId/notify-route").withAuthenticatedSession.withCSRFToken
        ).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bookings/$bookingId/notifications")
        flash(result).get("success").get should include("経路通知を送信しました")

        val logs = route(
          app,
          FakeRequest(GET, s"/bookings/$bookingId/notifications").withAuthenticatedSession
        ).get
        status(logs) shouldBe OK
        val body = contentAsString(logs)
        body should include("RouteNotified")
        body should include("VY-PRESET")
      }
    }

    "経路未紐付け予約への通知は予約詳細にエラーフラッシュ" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
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

        val result = route(
          app,
          FakeRequest(POST, s"/bookings/$bookingId/notify-route").withAuthenticatedSession.withCSRFToken
        ).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/bookings/$bookingId")
        flash(result).get("error").get should include("経路未紐付け")
      }
    }
  }
