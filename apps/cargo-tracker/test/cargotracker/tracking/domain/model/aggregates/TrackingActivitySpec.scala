package cargotracker.tracking.domain.model.aggregates

import cargotracker.tracking.domain.model.entities.TrackingActivityEvent
import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingLocation, TrackingNumber}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class TrackingActivitySpec extends AnyFunSuite with Matchers:

  test("issue: 採番済 TrackingNumber + bookingId で初期状態 NotReceived の TrackingActivity を生成する"):
    val tn = TrackingNumber.unsafeFrom("TN-000001")
    val Right(ta) = TrackingActivity.issue(tn, "BK-000001"): @unchecked
    ta.trackingNumber shouldBe tn
    ta.bookingId.value shouldBe "BK-000001"
    ta.transportStatus shouldBe TrackingStatus.NotReceived
    ta.version shouldBe 0
    ta.currentStatus shouldBe TrackingStatus.NotReceived

  test("issue: 空の bookingId は EmptyBookingId エラー"):
    val tn = TrackingNumber.unsafeFrom("TN-000002")
    TrackingActivity.issue(tn, "") shouldBe Left(TrackingActivity.EmptyBookingId)

  private def evt(eventType: String, sec: Long, loc: String = "JNTKO"): TrackingActivityEvent =
    TrackingActivityEvent(
      eventType = eventType,
      eventTime = Instant.ofEpochSecond(sec),
      location = TrackingLocation.of(loc),
      voyageNumber = None,
      routeDeviation = false
    )

  test("addEvent: 時系列逆順イベントは OutOfOrder で拒否される（H4）"):
    val tn = TrackingNumber.unsafeFrom("TN-000010")
    val Right(ta0) = TrackingActivity.issue(tn, "BK-000010"): @unchecked
    val Right(ta1) = ta0.addEvent(evt("Receive", 1000)): @unchecked
    ta1.addEvent(evt("Load", 500)) shouldBe Left(TrackingActivity.OutOfOrder)

  test("addEvent: 同時刻のイベントは許容される（H4 / 不変条件 2 同時刻含む）"):
    val tn = TrackingNumber.unsafeFrom("TN-000011")
    val Right(ta0) = TrackingActivity.issue(tn, "BK-000011"): @unchecked
    val Right(ta1) = ta0.addEvent(evt("Receive", 1000)): @unchecked
    val Right(ta2) = ta1.addEvent(evt("Load", 1000)): @unchecked
    ta2.events.size shouldBe 2
    ta2.transportStatus shouldBe TrackingStatus.Loaded

  test("reconstruct: 永続化からの復元"):
    val tn = TrackingNumber.unsafeFrom("TN-000003")
    val bid = TrackingBookingId.unsafeFrom("BK-000003")
    val ta = TrackingActivity.reconstruct(tn, bid, TrackingStatus.Loaded, version = 5)
    ta.transportStatus shouldBe TrackingStatus.Loaded
    ta.version shouldBe 5
