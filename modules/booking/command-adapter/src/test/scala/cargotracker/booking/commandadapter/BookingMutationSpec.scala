package cargotracker.booking.commandadapter

import cargotracker.booking.usecase.*
import cargotracker.sharedkernel.{Clock, Principal, Role, Ulid}
import io.circe.Json
import java.time.Instant
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*

/** GraphQL の Mutation。認可は入力検証より先。業務エラーはコードで返し、500 にしない（A4）。 */
class BookingMutationSpec extends AnyFunSuite:
  given ExecutionContext = ExecutionContext.parasitic

  private val at = Instant.parse("2026-11-04T00:00:00Z")
  private val clock = Clock.fixed(at)
  private def principal(role: Role) = Principal(Ulid.generate(clock, new java.util.Random(7)), role)

  private final class Env:
    val shippers = new Fakes.InMemoryShippers
    val bookings = new Fakes.InMemoryBookings
    val ids = new Fakes.SeededIds(clock, 9L)
    private val idem = new Fakes.InMemoryIdempotency
    val graphql = new BookingMutationGraphQL(
      new RegisterShipper(shippers, idem, ids, clock),
      new RegisterBooking(
        shippers,
        new Fakes.KnownPorts(Set("JPTYO", "NLRTM")),
        idem,
        bookings,
        new Fakes.SequentialNumbers,
        ids,
        clock
      )
    )
    def run(role: Role, q: String): Json =
      Await.result(graphql.execute(principal(role), q, Json.obj()), 5.seconds)
    def shipper(
        key: String = "s-1",
        email: String = "taro@example.com",
        ack: Boolean = false
    ): Json =
      run(
        Role.Sales,
        s"""mutation { registerShipper(idempotencyKey: "$key", name: "山田 太郎", address: "東京都", contact: "03-0000-0000", email: "$email", acknowledgedDuplicate: $ack) { shipperId } }"""
      )
    def shipperId(j: Json): String =
      j.hcursor.downField("data").downField("registerShipper").get[String]("shipperId").toOption.get
    def booking(
        shipper: String,
        key: String = "b-1",
        bookingId: String = Ulid.generate(clock, new java.util.Random(77)).value,
        weight: String = "120.5",
        destination: String = "NLRTM",
        role: Role = Role.Sales
    ): Json =
      run(
        role,
        s"""mutation { registerBooking(idempotencyKey: "$key", bookingId: "$bookingId", shipperId: "$shipper", itemName: "精密機器",
           |  weightKg: "$weight", lengthCm: "100", widthCm: "80", heightCm: "60", quantity: 3, origin: "JPTYO", destination: "$destination",
           |  preferredHandover: "2026-12-01", deadline: "2027-01-15") { bookingId number version } }""".stripMargin
      )

  private def code(j: Json) =
    j.hcursor.downField("errors").downArray.downField("extensions").get[String]("code").toOption
  private def fields(j: Json) =
    j.hcursor
      .downField("errors")
      .downArray
      .downField("extensions")
      .downField("fields")
      .values
      .toList
      .flatten
      .flatMap(_.hcursor.get[String]("field").toOption)

  test("US02-AC01: 営業担当者が荷主を登録でき、荷主 ID が返る") {
    val env = new Env
    val r = env.shipper()
    assert(Ulid.parse(env.shipperId(r)).isRight, r.noSpaces)
  }

  test("US04-AC02: 予約を登録でき、予約 ID・予約番号・最後のイベントの版が返る") {
    val env = new Env
    val s = env.shipperId(env.shipper())
    val r = env.booking(s)
    val b = r.hcursor.downField("data").downField("registerBooking")
    assert(
      b.get[String]("number") == Right("BK-000001") && b.get[Long]("version") == Right(1L),
      r.noSpaces
    )
  }

  test("認可（A8）: 荷主の登録も予約の登録も、営業担当者以外は FORBIDDEN。データは返らない") {
    val env = new Env
    val s = env.shipperId(env.shipper())
    Role.values.filter(_ != Role.Sales).foreach { role =>
      val r1 = env.run(
        role,
        """mutation { registerShipper(idempotencyKey: "k", name: "a", address: "b", contact: "c", email: "a@b.com") { shipperId } }"""
      )
      assert(code(r1) == Some("FORBIDDEN"), role.toString)
      assert(code(env.booking(s, role = role)) == Some("FORBIDDEN"), role.toString)
    }
  }

  test("認可は入力検証より先：権限の無いロールには、不正な入力でも FORBIDDEN を返す") {
    val env = new Env
    assert(code(env.booking("x", weight = "abc", role = Role.Accountant)) == Some("FORBIDDEN"))
  }

  test("A4: 不正な値（重量が数でない・港が同じ）は VALIDATION_FAILED で、不正な項目名を返す。500 にしない") {
    val env = new Env
    val s = env.shipperId(env.shipper())
    val r1 = env.booking(s, key = "b1", weight = "abc")
    assert(code(r1) == Some("VALIDATION_FAILED") && fields(r1).contains("weightKg"), r1.noSpaces)
    val r2 = env.booking(s, key = "b2", destination = "JPTYO")
    assert(code(r2) == Some("VALIDATION_FAILED") && fields(r2).contains("destination"), r2.noSpaces)
  }

  test("US02-AC02: 同じメールの荷主は DUPLICATE_EMAIL（既存の荷主 ID つき）。承知して続ければ登録できる") {
    val env = new Env
    val first = env.shipperId(env.shipper(key = "s-1"))
    val r = env.shipper(key = "s-2")
    assert(code(r) == Some("DUPLICATE_EMAIL"))
    assert(
      r.hcursor
        .downField("errors")
        .downArray
        .downField("extensions")
        .get[String]("existingShipperId") == Right(first)
    )
    assert(code(env.shipper(key = "s-3", ack = true)).isEmpty)
  }

  test("冪等性: 同じキーの再送は同じ結果。同じキーで内容が違えば IDEMPOTENCY_KEY_REUSED") {
    val env = new Env
    val a = env.shipper(key = "same")
    val b = env.shipper(key = "same")
    assert(env.shipperId(a) == env.shipperId(b))
    assert(
      code(env.shipper(key = "same", email = "other@example.com")) == Some("IDEMPOTENCY_KEY_REUSED")
    )
  }

  test("US04-AC01: 荷主が存在しなければ SHIPPER_NOT_FOUND") {
    val env = new Env
    val r = env.booking(Ulid.generate(clock, new java.util.Random(123)).value)
    assert(code(r) == Some("SHIPPER_NOT_FOUND"), r.noSpaces)
  }

  test("B1・A4: 同じ予約 ID を別のキーで再登録すると INVALID_TRANSITION（画面から不正な遷移を送って業務エラーが返る）") {
    val env = new Env
    val s = env.shipperId(env.shipper())
    val id = Ulid.generate(clock, new java.util.Random(55)).value
    assert(code(env.booking(s, key = "k1", bookingId = id)).isEmpty)
    assert(code(env.booking(s, key = "k2", bookingId = id)) == Some("INVALID_TRANSITION"))
  }

  test("必須の引数が無い・文法の誤りは、GraphQL の検証エラーとして返る（サーバーエラーにしない）") {
    val env = new Env
    val r =
      env.run(Role.Sales, """mutation { registerShipper(idempotencyKey: "k") { shipperId } }""")
    assert(r.hcursor.downField("errors").downArray.succeeded)
    assert(env.run(Role.Sales, "mutation {").hcursor.downField("errors").downArray.succeeded)
  }
