package cargotracker.booking.usecase

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{Clock, Principal, Role, Ulid}
import java.time.Instant
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.Await
import scala.concurrent.duration.*

class RegisterShipperSpec extends AnyFunSuite:
  import Fakes.given

  private val clock = Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"))
  private val sales = Principal(Ulid.generate(clock), Role.Sales)

  private final class Env:
    val shippers = new Fakes.InMemoryShippers
    val idempotency = new Fakes.InMemoryIdempotency
    val useCase = new RegisterShipper(shippers, idempotency, new Fakes.SeededIds(clock), clock)

  private def input(
      key: String = "key-1",
      name: String = "山田 太郎",
      email: String = "taro@example.com",
      acknowledgedDuplicate: Boolean = false
  ) = RegisterShipperInput(
    key,
    name,
    "東京都千代田区丸の内 1-1-1",
    "03-1234-5678",
    email,
    acknowledgedDuplicate
  )

  private def run(env: Env, p: Principal, in: RegisterShipperInput) =
    Await.result(env.useCase.execute(p, in), 2.seconds)

  test("US02-AC01: 営業担当者が個人の荷主を登録でき、荷主 ID が発行されて保存される") {
    val env = new Env
    val Right(out) = run(env, sales, input()): @unchecked
    assert(env.shippers.saved.keySet == Set(out.shipperId))
    assert(env.shippers.saved(out.shipperId).kind == ShipperKind.Individual)
  }

  test("認可（A8）: 営業担当者だけが荷主を登録できる。他の 5 ロールは Forbidden") {
    Role.values.foreach { role =>
      val env = new Env
      val r = run(env, Principal(sales.userId, role), input())
      if role == Role.Sales then assert(r.isRight, role.toString)
      else assert(r == Left(UseCaseError.Forbidden), role.toString)
    }
  }

  test("認可は入力検証より先：権限の無いロールには、不正な入力でも入力の仕様を教えず Forbidden を返す") {
    val env = new Env
    val r = run(env, Principal(sales.userId, Role.Accountant), input(name = "", email = "x"))
    assert(r == Left(UseCaseError.Forbidden))
  }

  test("入力が不正なら、不正な項目をまとめて返し、何も保存しない") {
    val env = new Env
    val Left(UseCaseError.ValidationFailed(errors)) =
      run(env, sales, input(name = " ", email = "bad")): @unchecked
    assert(errors.map(_.field).toSet == Set("name", "email"))
    assert(env.shippers.saved.isEmpty && env.idempotency.size == 0)
  }

  test("冪等性キーが空なら拒む") {
    val env = new Env
    val Left(UseCaseError.ValidationFailed(errors)) = run(env, sales, input(key = " ")): @unchecked
    assert(errors.map(_.field) == List("idempotencyKey"))
  }

  test("US02-AC02: 同じメールアドレスの荷主が登録済みなら、既存の荷主を示して登録しない") {
    val env = new Env
    val Right(first) = run(env, sales, input(key = "k1")): @unchecked
    val r = run(env, sales, input(key = "k2"))
    assert(r == Left(UseCaseError.DuplicateEmail(first.shipperId)))
    assert(env.shippers.saved.size == 1)
  }

  test("US02-AC02: 重複を承知して「登録を続ける」を選べば、同じメールでも登録できる") {
    val env = new Env
    val Right(first) = run(env, sales, input(key = "k1")): @unchecked
    val Right(second) = run(env, sales, input(key = "k2", acknowledgedDuplicate = true)): @unchecked
    assert(first.shipperId != second.shipperId && env.shippers.saved.size == 2)
  }

  test("冪等性: 同じキー・同じ入力の再送は、最初の結果を返し、二重に登録しない") {
    val env = new Env
    val Right(a) = run(env, sales, input(key = "same")): @unchecked
    val Right(b) = run(env, sales, input(key = "same")): @unchecked
    assert(a == b && env.shippers.saved.size == 1)
  }

  test("冪等性: 同じキーで内容が違う要求は拒む（別の要求を黙って上書きしない）") {
    val env = new Env
    run(env, sales, input(key = "same"))
    val r = run(env, sales, input(key = "same", name = "別の人", email = "other@example.com"))
    assert(r == Left(UseCaseError.IdempotencyKeyReused))
    assert(env.shippers.saved.size == 1)
  }

  test("冪等性: 失敗した要求のキーは使い捨てにならない（直して同じキーで再送できる）") {
    val env = new Env
    assert(run(env, sales, input(key = "retry", name = " ")).isLeft)
    assert(run(env, sales, input(key = "retry")).isRight)
  }

  test("冪等性: 別の利用者の同じキーは、別の要求として扱う") {
    val env = new Env
    val other = Principal(Ulid.generate(clock, new java.util.Random(99)), Role.Sales)
    val Right(a) = run(env, sales, input(key = "same", email = "a@example.com")): @unchecked
    val Right(b) = run(env, other, input(key = "same", email = "b@example.com")): @unchecked
    assert(a.shipperId != b.shipperId)
  }
