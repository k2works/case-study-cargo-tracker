package cargotracker.e2e

import cargotracker.support.AuthenticatedRequestSupport.*
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers.*

/** GET /bookings/:bookingId/routes の E2E テスト（IT3 タスク 2.7 / US08）。 */
class RouteCandidateEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

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
  }
