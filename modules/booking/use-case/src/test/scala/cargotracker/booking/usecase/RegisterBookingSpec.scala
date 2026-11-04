package cargotracker.booking.usecase

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{Clock, Principal, Role, Ulid}
import java.time.{Instant, LocalDate}
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.Await
import scala.concurrent.duration.*

class RegisterBookingSpec extends AnyFunSuite:
  import Fakes.given

  private val at = Instant.parse("2026-11-04T00:00:00Z")
  private val clock = Clock.fixed(at)
  private val sales = Principal(Ulid.generate(clock), Role.Sales)

  private final class Env:
    val ids = new Fakes.SeededIds(clock, 5L)
    val shippers = new Fakes.InMemoryShippers
    val ports = new Fakes.KnownPorts(Set("JPTYO", "NLRTM"))
    val idempotency = new Fakes.InMemoryIdempotency
    val bookings = new Fakes.InMemoryBookings
    val numbers = new Fakes.SequentialNumbers
    val useCase = new RegisterBooking(shippers, ports, idempotency, bookings, numbers, ids, clock)
    // 登録済みの荷主（US04 の前提）
    val shipperId: ShipperId = ShipperId(ids.next())
    Await.result(
      shippers.save(
        Shipper(
          shipperId,
          ShipperKind.Individual,
          ShipperName.parse("山田 太郎").toOption.get,
          Address.parse("東京都").toOption.get,
          ContactNumber.parse("03-0000-0000").toOption.get,
          EmailAddress.parse("taro@example.com").toOption.get
        ),
        AuditStamp(sales.userId, at)
      ),
      1.second
    )
    def newBookingId(): String = ids.next().value

  private def input(
      env: Env,
      key: String = "k-1",
      bookingId: Option[String] = None,
      origin: String = "JPTYO",
      destination: String = "NLRTM",
      weight: BigDecimal = BigDecimal("120.5"),
      shipper: Option[String] = None
  ) =
    RegisterBookingInput(
      idempotencyKey = key,
      bookingId = bookingId.getOrElse(env.newBookingId()),
      shipperId = shipper.getOrElse(env.shipperId.value.value),
      itemName = "精密機器",
      weightKg = weight,
      lengthCm = BigDecimal(100),
      widthCm = BigDecimal(80),
      heightCm = BigDecimal(60),
      quantity = 3,
      origin = origin,
      destination = destination,
      preferredHandover = LocalDate.of(2026, 12, 1),
      deadline = LocalDate.of(2027, 1, 15)
    )

  private def run(env: Env, p: Principal, in: RegisterBookingInput) =
    Await.result(env.useCase.execute(p, in), 2.seconds)

  test("US04-AC01・AC02: 登録済みの荷主の予約を登録でき、予約番号が発行されて仮受付になる") {
    val env = new Env
    val in = input(env)
    val Right(out) = run(env, sales, in): @unchecked
    assert(out.number.value == "BK-000001")
    val history = env.bookings.events(out.bookingId)
    assert(history.size == 1)
    assert(Booking.replay(history).isInstanceOf[BookingState.Received])
  }

  test("応答に、集約の ID と最後のイベントの版を含める（画面が版が追いつくまで待つ。BE 6.4）") {
    val env = new Env
    val Right(out) = run(env, sales, input(env)): @unchecked
    assert(out.version == 1L)
  }

  test("イベントの時刻は Clock から、イベント ID は IdGenerator から取る（now() を直接呼ばない）") {
    val env = new Env
    val Right(out) = run(env, sales, input(env)): @unchecked
    val e = env.bookings.events(out.bookingId).head
    assert(e.meta.occurredAt == at)
  }

  test("NFR-S10: イベントに、コマンドを実行した利用者（実行者）を残す") {
    val env = new Env
    val Right(out) = run(env, sales, input(env)): @unchecked
    assert(env.bookings.events(out.bookingId).head.meta.actor == Some(sales.userId))
  }

  test("認可（A8）: 営業担当者だけが予約を登録できる。他の 5 ロールは Forbidden") {
    Role.values.foreach { role =>
      val env = new Env
      val r = run(env, Principal(sales.userId, role), input(env))
      if role == Role.Sales then assert(r.isRight, role.toString)
      else assert(r == Left(UseCaseError.Forbidden), role.toString)
    }
  }

  test("認可は入力検証より先：権限の無いロールには不正な入力でも Forbidden を返す") {
    val env = new Env
    val r = run(
      env,
      Principal(sales.userId, Role.HandlingWorker),
      input(env, origin = "x", weight = BigDecimal(-1))
    )
    assert(r == Left(UseCaseError.Forbidden))
  }

  test("US04-AC01: 荷主が存在しなければ ShipperNotFound。予約も番号の発行も起きない") {
    val env = new Env
    val unknown = ShipperId(env.ids.next())
    val r = run(env, sales, input(env, shipper = Some(unknown.value.value)))
    assert(r == Left(UseCaseError.ShipperNotFound(unknown)))
    assert(env.bookings.events.isEmpty && env.numbers.issued == 0)
  }

  test("B11: 入力が不正なら、不正な項目をまとめて返し、何も起きない") {
    val env = new Env
    val Left(UseCaseError.ValidationFailed(errors)) =
      run(
        env,
        sales,
        input(env, origin = "JPTYO", destination = "JPTYO", weight = BigDecimal(0))
      ): @unchecked
    assert(errors.map(_.field).toSet == Set("destination", "weightKg"))
    assert(env.bookings.events.isEmpty && env.numbers.issued == 0)
  }

  test("港湾コードの形式が不正なら拒む（UN/LOCODE）") {
    val env = new Env
    val Left(UseCaseError.ValidationFailed(errors)) =
      run(env, sales, input(env, origin = "TOKYO1")): @unchecked
    assert(errors.map(_.field) == List("origin"))
  }

  test("目的地の形式が不正なら、項目は destination（出発地の欄に出さない）") {
    val env = new Env
    val Left(UseCaseError.ValidationFailed(errors)) =
      run(env, sales, input(env, destination = "ROTTERDAM")): @unchecked
    assert(errors.map(_.field) == List("destination"))
  }

  test("形式は正しいが参照データに無い港は、項目ごとに拒み、予約も番号の発行も起きない（受け付けたのに一覧に出ない予約を作らない）") {
    val env = new Env
    val Left(UseCaseError.ValidationFailed(errors)) =
      run(env, sales, input(env, origin = "JPOSA", destination = "ZZZZZ")): @unchecked
    assert(errors.map(_.field).toSet == Set("origin", "destination"))
    assert(env.bookings.events.isEmpty && env.numbers.issued == 0)
  }

  test("荷主 ID が ULID でなければ、項目は shipperId") {
    val env = new Env
    val Left(UseCaseError.ValidationFailed(errors)) =
      run(env, sales, input(env, shipper = Some("x"))): @unchecked
    assert(errors.map(_.field) == List("shipperId"))
  }

  test("読み取りモデルの列に入らない値は受け付けない（重量は小数 3 桁・10^9 未満、寸法は小数 2 桁・10^6 未満）") {
    val env = new Env
    def fields(in: RegisterBookingInput) =
      run(env, sales, in) match
        case Left(UseCaseError.ValidationFailed(es)) => es.map(_.field)
        case other                                   => fail(s"拒まれるはず: $other")
    assert(fields(input(env, weight = BigDecimal("0.0001"))) == List("weightKg"))
    assert(fields(input(env, weight = BigDecimal("1000000000"))) == List("weightKg"))
    assert(fields(input(env).copy(lengthCm = BigDecimal("0.001"))) == List("dimensions"))
    assert(fields(input(env).copy(heightCm = BigDecimal("1000000"))) == List("dimensions"))
    // 列に入る最大・最小は受け付ける
    assert(run(env, sales, input(env, weight = BigDecimal("0.001"))).isRight)
    assert(run(env, sales, input(env, key = "k-2", weight = BigDecimal("999999999.999"))).isRight)
  }

  test("集約 ID が ULID でなければ拒む") {
    val env = new Env
    val Left(UseCaseError.ValidationFailed(errors)) =
      run(env, sales, input(env, bookingId = Some("not-a-ulid"))): @unchecked
    assert(errors.map(_.field) == List("bookingId"))
  }

  test("冪等性: 同じキー・同じ入力の再送は最初の結果を返し、二重に登録せず、番号も飛ばない") {
    val env = new Env
    val in = input(env, key = "same")
    val Right(a) = run(env, sales, in): @unchecked
    val Right(b) = run(env, sales, in): @unchecked
    assert(a == b)
    assert(env.bookings.events.size == 1 && env.numbers.issued == 1)
  }

  test("冪等性: 同じキーで内容が違う要求は拒む") {
    val env = new Env
    run(env, sales, input(env, key = "same"))
    val r = run(env, sales, input(env, key = "same", destination = "SGSIN"))
    assert(r == Left(UseCaseError.IdempotencyKeyReused))
  }

  test("B1: 同じ予約 ID を別のキーで再び登録しようとすると、集約が InvalidTransition で拒む") {
    val env = new Env
    val id = Some(env.newBookingId())
    run(env, sales, input(env, key = "k1", bookingId = id))
    val r = run(env, sales, input(env, key = "k2", bookingId = id))
    assert(
      r == Left(UseCaseError.Booking(BookingError.InvalidTransition("Received", "RegisterBooking")))
    )
  }
