package cargotracker.auth.infrastructure

import cargotracker.auth.domain.{PasswordHash, Role, User}
import cargotracker.support.PostgresContainerSupport
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ScalikeJdbcUserRepositorySpec extends AnyFunSuite with Matchers with PostgresContainerSupport:

  test("ユーザーを保存して username で取得できる"):
    withContainers { container =>
      val app = buildApp(container)
      val repo = new ScalikeJdbcUserRepository

      val hash = PasswordHash.fromPlain("Sup3rSecret!").toOption.get
      val user = User
        .create("sales01", "sales01@example.com", hash, Set(Role.Sales))
        .toOption
        .get

      repo.save(user)
      val found = repo.findByUsername("sales01")

      found.isDefined shouldBe true
      found.get.email shouldBe "sales01@example.com"
      found.get.hasRole(Role.Sales) shouldBe true
      found.get.authenticate("Sup3rSecret!") shouldBe true
      app.stop()
    }

  test("複数ロールを持つユーザーを保存・復元できる"):
    withContainers { container =>
      val app = buildApp(container)
      val repo = new ScalikeJdbcUserRepository

      val hash = PasswordHash.fromPlain("Sup3rSecret!").toOption.get
      val user = User
        .create(
          "designer",
          "rd@example.com",
          hash,
          Set(Role.RouteDesigner, Role.Tracker)
        )
        .toOption
        .get

      repo.save(user)
      val found = repo.findByUsername("designer").get

      found.roles shouldBe Set(Role.RouteDesigner, Role.Tracker)
      app.stop()
    }

  test("存在しない username は None を返す"):
    withContainers { container =>
      val app = buildApp(container)
      val repo = new ScalikeJdbcUserRepository

      repo.findByUsername("ghost") shouldBe None
      app.stop()
    }
