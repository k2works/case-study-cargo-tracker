package cargotracker.tracking.domain.model.aggregates

import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingNumber}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

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

  test("reconstruct: 永続化からの復元"):
    val tn = TrackingNumber.unsafeFrom("TN-000003")
    val bid = TrackingBookingId.unsafeFrom("BK-000003")
    val ta = TrackingActivity.reconstruct(tn, bid, TrackingStatus.Loaded, version = 5)
    ta.transportStatus shouldBe TrackingStatus.Loaded
    ta.version shouldBe 5
