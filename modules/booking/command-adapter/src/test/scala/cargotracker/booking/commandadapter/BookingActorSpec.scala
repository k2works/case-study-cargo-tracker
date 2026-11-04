package cargotracker.booking.commandadapter

import cargotracker.booking.domain.*
import cargotracker.booking.usecase.CommandResult
import cargotracker.sharedkernel.{PortCode, Ulid}
import com.typesafe.config.ConfigFactory
import java.time.{Instant, LocalDate}
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin
import org.apache.pekko.persistence.testkit.scaladsl.PersistenceTestKit
import org.scalatest.funsuite.AnyFunSuiteLike
import scala.concurrent.Await
import scala.concurrent.duration.*

object BookingActorSpec:
  /** インメモリの Journal。**イベントを実際にシリアライズ**させ、バインディングの抜けを検出する（Java シリアライズは禁止）。 */
  val config = PersistenceTestKitPlugin.config.withFallback(
    ConfigFactory.parseString("""
      pekko.actor.allow-java-serialization = off
      pekko.actor.serializers.booking-event = "cargotracker.booking.commandadapter.BookingEventPekkoSerializer"
      pekko.actor.serialization-bindings."cargotracker.booking.domain.BookingEvent" = booking-event
      pekko.persistence.testkit.events.serialize = true
    """)
  )

/** 集約アクター（イベント履歴式。永続化してから返信する。復元できる）。 */
class BookingActorSpec
    extends ScalaTestWithActorTestKit(BookingActorSpec.config)
    with AnyFunSuiteLike:

  private val persistence = PersistenceTestKit(system)
  private val at = Instant.parse("2026-11-04T00:00:00Z")
  private var seed = 100L
  private def ulid(): Ulid = {
    seed += 1; Ulid.generate(cargotracker.sharedkernel.Clock.fixed(at), new java.util.Random(seed))
  }

  private val shipper = ShipperRef(ShipperId(ulid()), ShipperKind.Individual)
  private val cargo = CargoSpec.General(
    CargoCommon
      .create("精密機器", BigDecimal("120.5"), Dimensions.create(100, 80, 60).toOption.get, 3)
      .toOption
      .get
  )
  private val transport = TransportRequest
    .create(
      PortCode.parse("JPTYO").toOption.get,
      PortCode.parse("NLRTM").toOption.get,
      LocalDate.of(2026, 12, 1),
      LocalDate.of(2027, 1, 15)
    )
    .toOption
    .get

  private def register(id: BookingId) =
    BookingCommand.RegisterBooking(
      EventMeta(ulid(), at),
      id,
      BookingNumber.parse("BK-000001").toOption.get,
      shipper,
      cargo,
      transport
    )

  private def aggregates() = new PekkoBookingAggregates(system, 3.seconds)
  private def handle(a: PekkoBookingAggregates, id: BookingId, c: BookingCommand) =
    Await.result(a.handle(id, c), 5.seconds)

  test("登録すると、イベントが永続化されてから、最後のイベントの版 1 が返る") {
    val id = BookingId(ulid())
    val r = handle(aggregates(), id, register(id))
    assert(r == Right(CommandResult(1L)))
    assert(persistence.persistedInStorage(s"Booking|${id.value.value}").size == 1)
  }

  test("永続化されたイベントは、シリアライザ（Protobuf）を通っている：Journal から復元できる") {
    val id = BookingId(ulid())
    handle(aggregates(), id, register(id))
    val cmd = register(id)
    val stored = persistence.persistedInStorage(s"Booking|${id.value.value}").head
    assert(stored.isInstanceOf[BookingEvent.BookingRegistered])
  }

  test("B1: 登録済みの予約に再び登録すると拒まれ、イベントは増えない（拒否は永続化しない）") {
    val id = BookingId(ulid())
    val a = aggregates()
    handle(a, id, register(id))
    val r = handle(a, id, register(id))
    assert(r == Left(BookingError.InvalidTransition("Received", "RegisterBooking")))
    assert(persistence.persistedInStorage(s"Booking|${id.value.value}").size == 1)
  }

  test("復元: アクターを作り直しても（イベントから復元して）登録済みと分かり、再登録を拒む") {
    val id = BookingId(ulid())
    handle(aggregates(), id, register(id))
    val restarted = new PekkoBookingAggregates(system, 3.seconds) // 新しいレジストリ = 新しいアクター
    val r = handle(restarted, id, register(id))
    assert(r == Left(BookingError.InvalidTransition("Received", "RegisterBooking")))
  }

  test("別の予約 ID は独立している（互いの履歴に影響しない）") {
    val a = aggregates()
    val id1 = BookingId(ulid())
    val id2 = BookingId(ulid())
    assert(handle(a, id1, register(id1)).isRight)
    assert(handle(a, id2, register(id2)).isRight)
    assert(persistence.persistedInStorage(s"Booking|${id1.value.value}").size == 1)
    assert(persistence.persistedInStorage(s"Booking|${id2.value.value}").size == 1)
  }

  test("同じ集約への並行したコマンドは、1 件だけ成功する（アクターが直列に処理する）") {
    val id = BookingId(ulid())
    val a = aggregates()
    val fs = (1 to 8).map(_ => a.handle(id, register(id)))
    val rs = fs.map(Await.result(_, 5.seconds))
    assert(rs.count(_.isRight) == 1 && rs.count(_.isLeft) == 7)
    assert(persistence.persistedInStorage(s"Booking|${id.value.value}").size == 1)
  }
