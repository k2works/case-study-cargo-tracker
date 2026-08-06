package cargotracker.routing.domain.model.valueobjects

import cargotracker.shared.domain.Location
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class ScheduleSpec extends AnyFunSuite with Matchers:

  private val tyo = Location.unsafeFrom("JPTYO")
  private val yok = Location.unsafeFrom("JPYOK")
  private val lax = Location.unsafeFrom("USLAX")

  private val t0 = Instant.parse("2026-07-01T10:00:00Z")
  private val t1 = Instant.parse("2026-07-01T18:00:00Z")
  private val t2 = Instant.parse("2026-07-02T08:00:00Z")
  private val t3 = Instant.parse("2026-07-08T20:00:00Z")

  test("CarrierMovement: 出発地 == 到着地で SameOriginAndDestination"):
    CarrierMovement(tyo, tyo, t0, t1) shouldBe Left(CarrierMovement.SameOriginAndDestination)

  test("CarrierMovement: 出発時刻 ≥ 到着時刻で InvalidTimeRange"):
    CarrierMovement(tyo, yok, t1, t0) shouldBe Left(CarrierMovement.InvalidTimeRange)
    CarrierMovement(tyo, yok, t0, t0) shouldBe Left(CarrierMovement.InvalidTimeRange)

  test("Schedule: 空リストは EmptyMovements"):
    Schedule(Nil) shouldBe Left(Schedule.EmptyMovements)

  test("Schedule: 単一区間は生成可能で origin / destination が解決される"):
    val cm = CarrierMovement(tyo, yok, t0, t1).toOption.get
    val Right(s) = Schedule(List(cm)): @unchecked
    s.origin shouldBe tyo
    s.destination shouldBe yok

  test("Schedule: 連続する 2 区間は origin/destination が始終端"):
    val seg1 = CarrierMovement(tyo, yok, t0, t1).toOption.get
    val seg2 = CarrierMovement(yok, lax, t2, t3).toOption.get
    val Right(s) = Schedule(List(seg1, seg2)): @unchecked
    s.origin shouldBe tyo
    s.destination shouldBe lax

  test("Schedule: 区間連続性違反は DisconnectedMovements"):
    val seg1 = CarrierMovement(tyo, yok, t0, t1).toOption.get
    val seg2 = CarrierMovement(lax, tyo, t2, t3).toOption.get
    Schedule(List(seg1, seg2)) shouldBe Left(Schedule.DisconnectedMovements)

  test("Schedule: 時刻逆転は OutOfOrderTimes"):
    val seg1 = CarrierMovement(tyo, yok, t0, t2).toOption.get
    val seg2 = CarrierMovement(yok, lax, t1, t3).toOption.get
    Schedule(List(seg1, seg2)) shouldBe Left(Schedule.OutOfOrderTimes)
