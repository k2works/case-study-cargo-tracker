package cargotracker.app

import cargotracker.booking.commandadapter.PekkoBookingAggregates
import cargotracker.booking.domain.*
import cargotracker.booking.queryadapter.{BookingProjection, LagRecorder}
import cargotracker.sharedkernel.{Clock, PortCode, Ulid}
import cargotracker.testsupport.TestDatabase
import java.time.{Duration, Instant, LocalDate}
import java.util.Random
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.funsuite.AnyFunSuiteLike
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*
import slick.jdbc.PostgresProfile.api.*

/** イベント処理（読み取り Journal → 投影）。リトライ、待避（黙って捨てない）、停止せずに次へ進む、再読み込みの安全。 */
class EventProcessorSpec
    extends ScalaTestWithActorTestKit(LocalServer.actorSystemConfig)
    with AnyFunSuiteLike:
  given ExecutionContext = ExecutionContext.global

  private val at = Instant.parse("2026-11-04T01:00:00Z")
  private val rnd = new Random(61)
  private def ulid() = Ulid.generate(Clock.fixed(at), rnd)

  private final class Env:
    val testDb = TestDatabase.fresh()
    val db =
      Database.forURL(testDb.url, testDb.user, testDb.password, driver = "org.postgresql.Driver")
    val shipperId = ShipperId(ulid())
    run(
      sqlu"""INSERT INTO booking.shipper (shipper_id, shipper_type, name, address, contact, email, email_normalized,
             created_at, created_by, updated_at, updated_by)
             VALUES (${shipperId.value.value}, 'INDIVIDUAL', '山田', '東京', '03', 'a@b.com', 'a@b.com', now(), ${ulid().value}, now(), ${ulid().value})"""
    )
    val aggregates = new PekkoBookingAggregates(system, 3.seconds)
    val projection = new BookingProjection(db, Clock.fixed(at.plusSeconds(1)), (_: Duration) => ())
    val processor = new EventProcessor(
      system,
      projection,
      new SlickDeadLetters(db, Clock.fixed(at)),
      maxAttempts = 3,
      backoff = 10.millis,
      pollInterval = 20.millis
    )
    def run[A](a: DBIO[A]): A = Await.result(db.run(a), 5.seconds)
    def count(table: String): Int = run(sql"SELECT count(*) FROM #$table".as[Int].head)
    def register(shipper: ShipperId, number: String) =
      val id = BookingId(ulid())
      val cmd = BookingCommand.RegisterBooking(
        EventMeta(ulid(), at, None),
        id,
        BookingNumber.parse(number).toOption.get,
        ShipperRef(shipper, ShipperKind.Individual),
        CargoSpec.General(
          CargoCommon
            .create("品", BigDecimal(1), Dimensions.create(1, 1, 1).toOption.get, 1)
            .toOption
            .get
        ),
        TransportRequest
          .create(
            PortCode.parse("JPTYO").toOption.get,
            PortCode.parse("NLRTM").toOption.get,
            LocalDate.of(2026, 12, 1),
            LocalDate.of(2027, 1, 1)
          )
          .toOption
          .get
      )
      Await.result(aggregates.handle(id, cmd), 5.seconds)
      id

  private def eventually[A](what: String)(cond: => Boolean): Unit =
    val deadline = System.nanoTime() + 10.seconds.toNanos
    while !cond && System.nanoTime() < deadline do Thread.sleep(50)
    assert(cond, s"時間内に成立しなかった: $what")

  test("書き込まれたイベントが、読み取り Journal から投影される（booking_view に現れる）") {
    val env = new Env
    env.processor.start()
    val id = env.register(env.shipperId, "BK-000001")
    eventually("booking_view に反映")(env.count("booking.booking_view") == 1)
    assert(
      env.run(sql"SELECT booking_id FROM booking.booking_view".as[String].head) == id.value.value
    )
  }

  test("イベントが次々に書かれても、取りこぼさず順に投影される（ライブ追尾に頼らず、最後の位置から読み続ける）") {
    val env = new Env
    env.processor.start()
    (1 to 5).foreach(i => env.register(env.shipperId, f"BK-$i%06d"))
    eventually("5 件の反映")(env.count("booking.booking_view") == 5)
    Thread.sleep(300)
    env.register(env.shipperId, "BK-000006") // 少し間を置いて書かれたイベントも届く
    eventually("6 件目の反映")(env.count("booking.booking_view") == 6)
    assert(env.count("booking.booking_history") == 6)
  }

  test("先に書かれたイベントも、処理を始めた後に投影される（取りこぼさない）") {
    val env = new Env
    env.register(env.shipperId, "BK-000001")
    env.processor.start()
    eventually("既存のイベントの反映")(env.count("booking.booking_view") == 1)
  }

  test("失敗を黙って捨てない: 投影できないイベントは、リトライの後に待避先へ記録し、次のイベントの処理へ進む") {
    val env = new Env
    env.processor.start()
    // 荷主が存在しない → 外部キー違反で投影できない（同じ ActorSystem の他のテストのイベントとは、集約の ID で見分ける）
    val bad = env.register(ShipperId(ulid()), "BK-000001")
    val good = env.register(env.shipperId, "BK-000002")
    val badPid = s"Booking|${bad.value.value}"
    eventually("待避先への記録")(
      env.run(
        sql"SELECT count(*) FROM platform.dead_letter_event WHERE persistence_id = $badPid"
          .as[Int]
          .head
      ) == 1
    )
    eventually("後続のイベントの投影")(
      env.run(
        sql"SELECT count(*) FROM booking.booking_view WHERE booking_id = ${good.value.value}"
          .as[Int]
          .head
      ) == 1
    )
    val dl = env.run(
      sql"""SELECT projection_name, seq_nr, attempts, error, resolved_at IS NULL, octet_length(payload) > 0
            FROM platform.dead_letter_event WHERE persistence_id = $badPid"""
        .as[(String, Long, Int, String, Boolean, Boolean)]
        .head
    )
    assert(dl._1 == "booking-view" && dl._2 == 1L && dl._3 == 3 && dl._4.nonEmpty && dl._5 && dl._6)
    assert(
      env.run(
        sql"SELECT count(*) FROM booking.booking_view WHERE booking_id = ${bad.value.value}"
          .as[Int]
          .head
      ) == 0
    )
  }

  test("待避先の行は、読み直しで増えない（毒イベントを含めて止めて始め直しても 1 行のまま）") {
    val env = new Env
    val p1 = env.processor.start()
    val bad = env.register(ShipperId(ulid()), "BK-000001")
    val badPid = s"Booking|${bad.value.value}"
    def deadLetters = env.run(
      sql"SELECT count(*) FROM platform.dead_letter_event WHERE persistence_id = $badPid"
        .as[Int]
        .head
    )
    eventually("待避先への記録")(deadLetters == 1)
    p1.stop()
    env.processor.start() // 先頭から読み直し、毒イベントをもう一度待避しようとする
    val good = env.register(env.shipperId, "BK-000002")
    eventually("読み直しの完了（後続の反映）")(
      env.run(
        sql"SELECT count(*) FROM booking.booking_view WHERE booking_id = ${good.value.value}"
          .as[Int]
          .head
      ) == 1
    )
    assert(deadLetters == 1)
  }

  test("A6: 処理を止めて始め直し、同じイベントを読み直しても、行は増えない（再読み込みは安全）") {
    val env = new Env
    val p1 = env.processor.start()
    env.register(env.shipperId, "BK-000001")
    eventually("1 件目の反映")(env.count("booking.booking_view") == 1)
    p1.stop()
    env.processor.start() // 先頭から読み直す
    env.register(env.shipperId, "BK-000002")
    eventually("2 件目の反映")(env.count("booking.booking_view") == 2)
    assert(env.count("booking.booking_history") == 2)
  }
