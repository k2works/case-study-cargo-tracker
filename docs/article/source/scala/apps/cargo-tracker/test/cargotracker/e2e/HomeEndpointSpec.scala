package cargotracker.e2e

import cargotracker.support.AuthenticatedRequestSupport.*
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers.*

/** HomeController の統合テスト（IT3 タスク 0.1 残り）。 */
class HomeEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  "GET /" should {
    "未認証時はログイン画面にリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/")).get
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/login")
      }
    }

    "認証済みでダッシュボードを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/").withAuthenticatedSession).get
        status(result) shouldBe OK
        contentAsString(result) should include("CargoTracker")
      }
    }
  }
