package cargotracker.e2e

import cargotracker.auth.domain.model.aggregates.User
import cargotracker.auth.domain.model.repositories.UserRepository
import cargotracker.auth.domain.model.valueobjects.{PasswordHash, Role}
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class AuthEndpointSpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  private def seedUser(repo: UserRepository): Unit =
    val hash = PasswordHash.fromPlain("Sup3rSecret!").toOption.get
    val user = User
      .create("sales01", "sales01@example.com", hash, Set(Role.Sales))
      .toOption
      .get
    repo.save(user)

  "GET /login" should {
    "ログインフォームを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val result = route(app, FakeRequest(GET, "/login")).get
        status(result) shouldBe OK
        contentAsString(result) should include("CargoTracker")
        contentAsString(result) should include("ログイン")
      }
    }
  }

  "POST /login" should {
    "認証成功時にダッシュボードへリダイレクトしセッションを発行する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        seedUser(app.injector.instanceOf[UserRepository])

        val request = FakeRequest(POST, "/login")
          .withFormUrlEncodedBody(
            "username" -> "sales01",
            "password" -> "Sup3rSecret!"
          )
          .withCSRFToken
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/")
        session(result).get("username") shouldBe Some("sales01")
        session(result).get("roles").get should include("Sales")
      }
    }

    "認証失敗時に Unauthorized を返しエラーメッセージを表示する" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        seedUser(app.injector.instanceOf[UserRepository])

        val request = FakeRequest(POST, "/login")
          .withFormUrlEncodedBody(
            "username" -> "sales01",
            "password" -> "WrongPass1"
          )
          .withCSRFToken
        val result = route(app, request).get

        status(result) shouldBe UNAUTHORIZED
        contentAsString(result) should include(
          "ユーザー名またはパスワードが正しくありません"
        )
      }
    }
  }

  "POST /logout" should {
    "セッションを破棄しログイン画面へリダイレクトする" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val request = FakeRequest(POST, "/logout").withCSRFToken
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/login")
        session(result).get("username") shouldBe None
      }
    }
  }
