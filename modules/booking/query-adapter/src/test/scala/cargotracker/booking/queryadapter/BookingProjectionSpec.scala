package cargotracker.booking.queryadapter

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{Clock, PortCode, Ulid}
import cargotracker.testsupport.TestDatabase
import java.time.{Duration, Instant, LocalDate}
import java.util.Random
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*
import slick.jdbc.PostgresProfile.api.*

/** 読み取りモデルの投影（イベント → booking_view・booking_history）。冪等・版の順・失敗を捨てない。 */
class BookingProjectionSpec extends AnyFunSuite:
  given ExecutionContext = ExecutionContext.global

  private val eventAt = Instant.parse("2026-11-04T01:00:00Z")
  private val now = Instant.parse("2026-11-04T01:00:02.500Z")
  private val rnd = new Random(33)
  private def ulid() = Ulid.generate(Clock.fixed(eventAt), rnd)
  private val actor = ulid()

  private final class Lags extends LagRecorder:
    val recorded: mutable.Buffer[Duration] = mutable.Buffer.empty
    def record(lag: Duration): Unit = recorded += lag

  private final class Env:
    val testDb = TestDatabase.fresh()
    val db =
      Database.forURL(testDb.url, testDb.user, testDb.password, driver = "org.postgresql.Driver")
    val lags = new Lags
    val projection = new BookingProjection(db, Clock.fixed(now), lags)
    val shipperId = ShipperId(ulid())
    run(
      sqlu"""INSERT INTO booking.shipper (shipper_id, shipper_type, name, address, contact, email, email_normalized,
             created_at, created_by, updated_at, updated_by)
             VALUES (${shipperId.value.value}, 'INDIVIDUAL', '山田', '東京', '03', 'a@b.com', 'a@b.com', now(), ${actor.value}, now(), ${actor.value})"""
    )
    def run[A](a: DBIO[A]): A = Await.result(db.run(a), 5.seconds)
    def count(table: String): Int = run(sql"SELECT count(*) FROM #$table".as[Int].head)

    def registered(
        shipper: ShipperId = shipperId,
        number: String = "BK-000001"
    ): BookingEvent.BookingRegistered =
      BookingEvent.BookingRegistered(
        EventMeta(ulid(), eventAt, Some(actor)),
        BookingId(ulid()),
        BookingNumber.parse(number).toOption.get,
        ShipperRef(shipper, ShipperKind.Individual),
        CargoSpec.General(
          CargoCommon
            .create(
              "精密機器",
              BigDecimal("120.5"),
              Dimensions.create(100, BigDecimal("80.25"), 60).toOption.get,
              3
            )
            .toOption
            .get
        ),
        TransportRequest
          .create(
            PortCode.parse("JPTYO").toOption.get,
            PortCode.parse("NLRTM").toOption.get,
            LocalDate.of(2026, 12, 1),
            LocalDate.of(2027, 1, 15)
          )
          .toOption
          .get
      )

  private def apply(env: Env, e: BookingEvent, seqNr: Long) =
    Await.result(env.projection.apply(ProjectedEvent(e, seqNr)), 5.seconds)

  test("登録イベントは、booking_view に 1 行（状態は RECEIVED・版 1）と、booking_history に REGISTERED を作る") {
    val env = new Env
    val e = env.registered()
    assert(apply(env, e, 1) == ProjectionResult.Applied)
    val row = env.run(
      sql"""SELECT booking_number, shipper_id, status, aggregate_version, last_event_id, weight_kg::text, length_cm::text,
                   origin_port, destination_port, cargo_kind, quantity, array_length(available_commands, 1) IS NULL, updated_at = ${java.sql.Timestamp
          .from(eventAt)}
            FROM booking.booking_view"""
        .as[
          (
              String,
              String,
              String,
              Long,
              String,
              String,
              String,
              String,
              String,
              String,
              Int,
              Boolean,
              Boolean
          )
        ]
        .head
    )
    assert(
      row == (
        "BK-000001",
        env.shipperId.value.value,
        "RECEIVED",
        1L,
        e.meta.eventId.value,
        "120.500",
        "100.00",
        "JPTYO",
        "NLRTM",
        "GENERAL",
        3,
        true,
        true
      )
    )
    val history = env.run(
      sql"SELECT event_id, kind, actor_user_id, occurred_at = ${java.sql.Timestamp.from(eventAt)} FROM booking.booking_history"
        .as[(String, String, String, Boolean)]
        .head
    )
    assert(history == (e.meta.eventId.value, "REGISTERED", actor.value, true))
  }

  test("A6 冪等: 同じイベントを 2 回流しても、行は増えず、結果は AlreadyApplied") {
    val env = new Env
    val e = env.registered()
    assert(apply(env, e, 1) == ProjectionResult.Applied)
    assert(apply(env, e, 1) == ProjectionResult.AlreadyApplied)
    assert(env.count("booking.booking_view") == 1 && env.count("booking.booking_history") == 1)
  }

  test("順序: 版 1 より先に版 2 が来たら適用せず、期待する版を示して保留にする（欠けを埋めない）") {
    val env = new Env
    assert(apply(env, env.registered(), 2) == ProjectionResult.Gap(expected = 1, actual = 2))
    assert(env.count("booking.booking_view") == 0 && env.count("booking.booking_history") == 0)
  }

  test("再構築: 読み取りモデルを空にして、同じイベントを流し直すと、同じ内容に戻る") {
    val env = new Env
    val e1 = env.registered(number = "BK-000001")
    val e2 = env.registered(number = "BK-000002")
    List(e1, e2).foreach(apply(env, _, 1))
    val before = env.run(
      sql"SELECT booking_id, booking_number, status, aggregate_version, last_event_id FROM booking.booking_view ORDER BY booking_number"
        .as[(String, String, String, Long, String)]
    )
    env.run(sqlu"DELETE FROM booking.booking_history")
    env.run(sqlu"DELETE FROM booking.booking_view")
    List(e1, e2).foreach(apply(env, _, 1))
    val after = env.run(
      sql"SELECT booking_id, booking_number, status, aggregate_version, last_event_id FROM booking.booking_view ORDER BY booking_number"
        .as[(String, String, String, Long, String)]
    )
    assert(after == before && env.count("booking.booking_history") == 2)
  }

  test("失敗を捨てない: 荷主が存在しない（外部キー違反）なら Future が失敗し、一部の行も残さない") {
    val env = new Env
    val e = env.registered(shipper = ShipperId(ulid()))
    val r = scala.util.Try(apply(env, e, 1))
    assert(r.isFailure)
    assert(env.count("booking.booking_view") == 0 && env.count("booking.booking_history") == 0)
  }

  test("反映遅延の計測点: 適用したとき、イベントの発生時刻との差を記録する。重複・保留では記録しない") {
    val env = new Env
    val e = env.registered()
    apply(env, e, 1)
    assert(env.lags.recorded.toList == List(Duration.ofMillis(2500)))
    apply(env, e, 1)
    apply(env, env.registered(number = "BK-000009"), 3)
    assert(env.lags.recorded.size == 1)
  }

  test("実行できる操作は、ドメインの述語で計算した値を持つ（画面が状態を再判定しない）") {
    val env = new Env
    val e = env.registered()
    apply(env, e, 1)
    val cmds =
      env.run(sql"SELECT available_commands::text FROM booking.booking_view".as[String].head)
    assert(cmds == "{}" && Booking.availableCommands(Booking.replay(Seq(e))).isEmpty)
  }
