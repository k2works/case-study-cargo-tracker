package cargotracker.booking.commandadapter

import cargotracker.booking.domain.*
import cargotracker.booking.usecase.*
import cargotracker.sharedkernel.{Clock, Ulid}
import cargotracker.testsupport.TestDatabase
import java.time.{Duration, Instant}
import java.util.Random
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import slick.jdbc.PostgresProfile.api.*

/** 出力ポートの実装（PostgreSQL の実物）：冪等性キーの保存、予約番号の採番、ID の生成。 */
class PlatformAdaptersSpec extends AnyFunSuite:
  given ExecutionContext = ExecutionContext.global

  private val t0 = Instant.parse("2026-11-04T00:00:00Z")
  private val rnd = new Random(81)
  private def ulid() = Ulid.generate(Clock.fixed(t0), rnd)

  private final class Env:
    val testDb = TestDatabase.fresh()
    val db =
      Database.forURL(testDb.url, testDb.user, testDb.password, driver = "org.postgresql.Driver")
    def store(at: Instant = t0) = new SlickIdempotencyStore(db, Clock.fixed(at))

  private def await[A](f: Future[A]): A = Await.result(f, 5.seconds)

  private val shipperOut = StoredResult(
    "h1",
    IdempotentOutput.ShipperRegistered(
      ShipperId(Ulid.parse("01ARZ3NDEKTSV4RRFFQ69G5FAV").toOption.get)
    )
  )
  private val bookingOut = StoredResult(
    "h2",
    IdempotentOutput.BookingRegistered(
      BookingId(Ulid.parse("01ARZ3NDEKTSV4RRFFQ69G5FAW").toOption.get),
      BookingNumber.parse("BK-000042").toOption.get,
      1L
    )
  )

  test("冪等性キー: 保存した最初の結果を、同じ利用者・同じキーで取り出せる（荷主・予約の両方）") {
    val env = new Env
    val owner = ulid()
    await(env.store().save(owner, "k1", shipperOut))
    await(env.store().save(owner, "k2", bookingOut))
    assert(await(env.store().find(owner, "k1")) == Some(shipperOut))
    assert(await(env.store().find(owner, "k2")) == Some(bookingOut))
    assert(await(env.store().find(owner, "none")).isEmpty)
  }

  test("冪等性キー: 別の利用者の同じキーは、別の要求として扱う") {
    val env = new Env
    val a = ulid()
    val b = ulid()
    await(env.store().save(a, "same", shipperOut))
    assert(await(env.store().find(b, "same")).isEmpty)
  }

  test("冪等性キー: 保持は 7 日（NFR-D02）。期限を過ぎたキーは別の要求として扱う") {
    val env = new Env
    val owner = ulid()
    await(env.store(t0).save(owner, "k", shipperOut))
    assert(await(env.store(t0.plus(Duration.ofDays(7)).minusSeconds(1)).find(owner, "k")).isDefined)
    assert(await(env.store(t0.plus(Duration.ofDays(7)).plusSeconds(1)).find(owner, "k")).isEmpty)
  }

  test("冪等性キー: 同じキーに 2 度目の保存があっても、最初の結果が残る（上書きしない）") {
    val env = new Env
    val owner = ulid()
    await(env.store().save(owner, "k", shipperOut))
    await(env.store().save(owner, "k", bookingOut))
    assert(await(env.store().find(owner, "k")) == Some(shipperOut))
  }

  test("予約番号: シーケンスから BK- + 6 桁で発行する。並行して発行しても重複しない") {
    val env = new Env
    val issuer = new SequenceBookingNumberIssuer(env.db)
    assert(await(issuer.next()).value == "BK-000001")
    assert(await(issuer.next()).value == "BK-000002")
    val many = await(Future.sequence((1 to 20).map(_ => issuer.next()))).map(_.value)
    assert(many.toSet.size == 20)
  }

  test("ID の生成: ULID で、新しいものほど文字列として大きい") {
    var now = t0
    val clock = new Clock { def now(): Instant = SecureIdGeneratorTestClock.current }
    SecureIdGeneratorTestClock.current = t0
    val gen = new SecureIdGenerator(clock)
    val a = gen.next()
    SecureIdGeneratorTestClock.current = t0.plusSeconds(1)
    val b = gen.next()
    assert(Ulid.parse(a.value).isRight && a.value < b.value)
  }

private object SecureIdGeneratorTestClock:
  @volatile var current: Instant = Instant.EPOCH
