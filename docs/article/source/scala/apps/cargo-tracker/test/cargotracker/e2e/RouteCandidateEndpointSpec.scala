package cargotracker.e2e

import cargotracker.booking.application.commandservices.{BookCargoCommand, BookingCommandService}
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.support.AuthenticatedRequestSupport.*
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import java.time.LocalDate

/** GET /bookings/:bookingId/routes の E2E テスト（IT3 タスク 2.7 / US08）。 */
class RouteCandidateEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def seedShipper(repo: ShipperRepository): String =
    val id = repo.nextIdentity()
    repo.save(
      Shipper
        .individual(id, "経路用荷主", s"empty-${id.value}@example.com", "0", "addr")
        .toOption
        .get
    )
    id.value

  "GET /bookings/:bookingId/routes" should {

    "存在しない予約 ID なら 404 を返す" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result =
          route(app, FakeRequest(GET, "/bookings/UNKNOWN/routes").withAuthenticatedSession).get
        status(result) shouldBe NOT_FOUND
      }
    }

    "未認証なら /login にリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/bookings/X/routes")).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/login")
      }
    }

    "航海 seed なしでも 200 を返し条件緩和ガイドを表示する（ハッピーパス回帰）" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val shipperCode = seedShipper(app.injector.instanceOf[ShipperRepository])
        val bookingId = app.injector
          .instanceOf[BookingCommandService]
          .book(
            BookCargoCommand(
              shipperCode = shipperCode,
              origin = "JPTYO",
              destination = "USLAX",
              arrivalDeadline = LocalDate.parse("2099-09-30"),
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

        val result =
          route(app, FakeRequest(GET, s"/bookings/$bookingId/routes").withAuthenticatedSession).get
        status(result) shouldBe OK
        val body = contentAsString(result)
        body should include("条件に合致する経路候補が見つかりませんでした")
        body should include("貨物種別の制約")
        body should include("data-us10-relink=\"true\"")
      }
    }
  }
