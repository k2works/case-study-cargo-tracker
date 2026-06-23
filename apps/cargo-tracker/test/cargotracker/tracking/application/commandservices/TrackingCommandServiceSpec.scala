package cargotracker.tracking.application.commandservices

import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingNumber}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class TrackingCommandServiceSpec extends AnyFunSuite with Matchers:

  private class InMemoryRepo extends TrackingActivityRepository:
    val store: mutable.Map[String, TrackingActivity] = mutable.Map.empty
    private val seq: java.util.concurrent.atomic.AtomicLong = java.util.concurrent.atomic.AtomicLong(0L)
    override def nextTrackingNumber(): TrackingNumber =
      TrackingNumber.fromSequence(seq.incrementAndGet())
    override def findByTrackingNumber(tn: TrackingNumber): Option[TrackingActivity] =
      store.values.find(_.trackingNumber == tn)
    override def findByBookingId(bid: TrackingBookingId): Option[TrackingActivity] =
      store.get(bid.value)
    override def save(activity: TrackingActivity): Unit =
      store(activity.bookingId.value) = activity
    override def appendEvent(
        activity: TrackingActivity,
        newEvent: cargotracker.tracking.domain.model.entities.TrackingActivityEvent
    ): TrackingActivity =
      val current = store(activity.bookingId.value)
      val updated = current.addEvent(newEvent).fold(_ => current, identity)
      val withNewVersion = TrackingActivity.reconstruct(
        trackingNumber = updated.trackingNumber,
        bookingId = updated.bookingId,
        transportStatus = updated.transportStatus,
        events = updated.events,
        version = updated.version + 1
      )
      store(activity.bookingId.value) = withNewVersion
      withNewVersion

  test("assign: 新規予約に対して採番し TrackingActivity を初期化（NotReceived）"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val Right(ta) = svc.assign(AssignTrackingNumberCommand("BK-000001")): @unchecked
    ta.trackingNumber.value shouldBe "TN-000001"
    ta.bookingId.value shouldBe "BK-000001"
    ta.transportStatus shouldBe TrackingStatus.NotReceived
    repo.store.size shouldBe 1

  test("assign: 同一予約への 2 回目呼出は冪等成功（既存番号を返す）"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val Right(first) = svc.assign(AssignTrackingNumberCommand("BK-000002")): @unchecked
    val Right(second) = svc.assign(AssignTrackingNumberCommand("BK-000002")): @unchecked
    first.trackingNumber shouldBe second.trackingNumber
    repo.store.size shouldBe 1

  test("assign: 空の予約 ID は Left"):
    val svc = new TrackingCommandService(new InMemoryRepo)
    svc.assign(AssignTrackingNumberCommand("")).isLeft shouldBe true
