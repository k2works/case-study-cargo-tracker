package cargotracker.e2e

import cargotracker.booking.application.commandservices.{BookCargoCommand, BookingCommandService}
import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.CargoType
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.support.AuthenticatedRequestSupport.*
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import java.time.{Instant, LocalDate}

/** US08 経路候補算出の統合 E2E（IT3 タスク 2.9）。 直行 / 危険物フィルタ / 期限超過の 3 シナリオを Voyage + Booking 永続化込みで検証する。 */
class RouteCandidateIntegrationSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def seedShipper(repo: ShipperRepository): String =
    val id = repo.nextIdentity()
    val s = Shipper
      .individual(id, "経路用荷主", s"route-${id.value}@example.com", "0", "addr")
      .toOption
      .get
    repo.save(s)
    id.value

  private def book(
      bookingService: BookingCommandService,
      shipperCode: String,
      origin: String,
      destination: String,
      deadline: LocalDate,
      cargoType: String = "General"
  ): String =
    bookingService
      .book(
        BookCargoCommand(
          shipperCode = shipperCode,
          origin = origin,
          destination = destination,
          arrivalDeadline = deadline,
          cargoType = cargoType,
          weightKg = 1000,
          description = None,
          quantity = None,
          hazardousClass = if cargoType == "Hazardous" then Some("3") else None,
          hazardousUnNumber = if cargoType == "Hazardous" then Some("UN1170") else None,
          hazardousProperName = if cargoType == "Hazardous" then Some("ETHANOL") else None,
          refrigerationMinTemp = None,
          refrigerationMaxTemp = None,
          refrigerationUnit = None
        )
      )
      .toOption
      .get
      .bookingId
      .value

  private def seedVoyage(
      repo: VoyageRepository,
      vn: String,
      from: String,
      to: String,
      depart: String,
      arrive: String,
      supported: Set[CargoType] = Set(CargoType.General)
  ): Unit =
    val cm = CarrierMovement(
      cargotracker.shared.domain.Location.unsafeFrom(from),
      cargotracker.shared.domain.Location.unsafeFrom(to),
      Instant.parse(depart),
      Instant.parse(arrive)
    ).toOption.get
    val schedule = Schedule(List(cm)).toOption.get
    repo.save(Voyage.register(VoyageNumber.unsafeFrom(vn), schedule, "Vessel", "CC", supported))

  "POST /bookings/:bookingId/routes/:idx/confirm" should {

    "直行便を選んで確定すると success フラッシュとともに候補画面に戻る" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val shipperCode = seedShipper(app.injector.instanceOf[ShipperRepository])
        seedVoyage(
          app.injector.instanceOf[VoyageRepository],
          "VY-CONFIRM",
          "JPTYO",
          "USLAX",
          "2099-07-01T10:00:00Z",
          "2099-07-10T18:00:00Z"
        )
        val bookingId = book(
          app.injector.instanceOf[BookingCommandService],
          shipperCode,
          "JPTYO",
          "USLAX",
          LocalDate.parse("2099-12-31")
        )
        val confirm = route(
          app,
          FakeRequest(POST, s"/bookings/$bookingId/routes/0/confirm").withAuthenticatedSession.withCSRFToken
        ).get
        status(confirm) shouldBe SEE_OTHER
        redirectLocation(confirm) shouldBe Some(s"/bookings/$bookingId/routes")
        flash(confirm).get("success") shouldBe Some("経路を確定しました")
      }
    }

    "存在しない予約 ID の確定は 404" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(
          app,
          FakeRequest(POST, "/bookings/UNKNOWN/routes/0/confirm").withAuthenticatedSession.withCSRFToken
        ).get
        status(result) shouldBe NOT_FOUND
      }
    }
  }

  "GET /bookings/:bookingId/routes" should {

    "直行便があれば期限内候補として表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val shipperCode = seedShipper(app.injector.instanceOf[ShipperRepository])
        seedVoyage(
          app.injector.instanceOf[VoyageRepository],
          "VY-DIRECT",
          "JPTYO",
          "USLAX",
          "2099-07-01T10:00:00Z",
          "2099-07-10T18:00:00Z"
        )
        val bookingId = book(
          app.injector.instanceOf[BookingCommandService],
          shipperCode,
          "JPTYO",
          "USLAX",
          LocalDate.parse("2099-12-31")
        )
        val result =
          route(app, FakeRequest(GET, s"/bookings/$bookingId/routes").withAuthenticatedSession).get
        status(result) shouldBe OK
        val body = contentAsString(result)
        body should include("期限内到着候補")
        body should include("VY-DIRECT")
      }
    }

    "危険物予約に対し非対応の航海は候補化されない" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val shipperCode = seedShipper(app.injector.instanceOf[ShipperRepository])
        seedVoyage(
          app.injector.instanceOf[VoyageRepository],
          "VY-GEN",
          "JPTYO",
          "USLAX",
          "2099-07-01T10:00:00Z",
          "2099-07-10T18:00:00Z",
          Set(CargoType.General)
        )
        val bookingId = book(
          app.injector.instanceOf[BookingCommandService],
          shipperCode,
          "JPTYO",
          "USLAX",
          LocalDate.parse("2099-12-31"),
          cargoType = "Hazardous"
        )
        val result =
          route(app, FakeRequest(GET, s"/bookings/$bookingId/routes").withAuthenticatedSession).get
        status(result) shouldBe OK
        contentAsString(result) should include("条件に合致する経路候補が見つかりませんでした")
      }
    }

    "希望着日を超過する候補のみなら期限超過セクションと緩和ガイドを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val shipperCode = seedShipper(app.injector.instanceOf[ShipperRepository])
        seedVoyage(
          app.injector.instanceOf[VoyageRepository],
          "VY-LATE",
          "JPYOK",
          "USNYC",
          "2099-07-01T10:00:00Z",
          "2099-12-30T18:00:00Z"
        )
        val bookingId = book(
          app.injector.instanceOf[BookingCommandService],
          shipperCode,
          "JPYOK",
          "USNYC",
          LocalDate.parse("2099-07-15")
        )
        val result =
          route(
            app,
            FakeRequest(GET, s"/bookings/$bookingId/routes").withAuthenticatedSession
          ).get
        status(result) shouldBe OK
        val body = contentAsString(result)
        body should include("到着可能な経路は見つかりませんでした")
        body should include("期限超過候補")
        body should include("VY-LATE")
      }
    }
  }
