package cargotracker.auth.application.commandservices

import cargotracker.auth.domain.model.aggregates.User
import cargotracker.auth.domain.model.repositories.UserRepository
import cargotracker.auth.domain.model.valueobjects.{PasswordHash, Role}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Clock, Instant, ZoneOffset}
import scala.collection.mutable

class AuthCommandServiceSpec extends AnyFunSuite with Matchers:

  private val fixedInstant = Instant.parse("2026-07-01T09:00:00Z")
  private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

  private val plainPassword = "Adm1nPass!"
  private val passwordHash = PasswordHash.fromPlain(plainPassword).toOption.get
  private val adminUser = User
    .create("admin", "admin@example.com", passwordHash, Set(Role.MasterAdmin, Role.Sales))
    .toOption
    .get

  private class InMemoryUserRepository(seed: User*) extends UserRepository:
    private val store: mutable.Map[String, User] =
      mutable.Map.from(seed.map(u => u.username -> u))
    override def findByUsername(username: String): Option[User] = store.get(username)
    override def save(user: User): Unit = store.update(user.username, user)

  test("正しい資格情報で AuthenticatedSession を返す（lastAccessedAt は Clock 由来）"):
    val service = new AuthCommandService(new InMemoryUserRepository(adminUser), fixedClock)

    val Right(session) = service.authenticate("admin", plainPassword): @unchecked

    session.username shouldBe "admin"
    session.lastAccessedAt shouldBe fixedInstant
    session.rolesCsv.split(",").toSet shouldBe Set("MasterAdmin", "Sales")

  test("存在しないユーザーは InvalidCredentials"):
    val service = new AuthCommandService(new InMemoryUserRepository(), fixedClock)

    service.authenticate("ghost", "anything") shouldBe Left(
      AuthCommandService.AuthError.InvalidCredentials
    )

  test("誤ったパスワードは InvalidCredentials"):
    val service = new AuthCommandService(new InMemoryUserRepository(adminUser), fixedClock)

    service.authenticate("admin", "WrongPass!") shouldBe Left(
      AuthCommandService.AuthError.InvalidCredentials
    )

  test("複数ロールを CSV に変換する"):
    val multiRoleUser = User
      .create(
        "multi",
        "multi@example.com",
        passwordHash,
        Set(Role.Sales, Role.RouteDesigner, Role.Tracker)
      )
      .toOption
      .get
    val service =
      new AuthCommandService(new InMemoryUserRepository(multiRoleUser), fixedClock)

    val Right(session) = service.authenticate("multi", plainPassword): @unchecked
    session.rolesCsv.split(",").toSet shouldBe Set("Sales", "RouteDesigner", "Tracker")
