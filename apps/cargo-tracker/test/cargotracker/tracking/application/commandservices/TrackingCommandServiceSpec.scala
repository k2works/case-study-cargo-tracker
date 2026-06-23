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
    override def appendException(
        activity: TrackingActivity,
        newException: cargotracker.tracking.domain.model.entities.TrackingExceptionEvent
    ): TrackingActivity =
      val current = store(activity.bookingId.value)
      val updated = current.addException(newException)
      val withNewVersion = TrackingActivity.reconstruct(
        trackingNumber = updated.trackingNumber,
        bookingId = updated.bookingId,
        transportStatus = updated.transportStatus,
        events = updated.events,
        version = updated.version + 1,
        exceptions = updated.exceptions
      )
      store(activity.bookingId.value) = withNewVersion
      withNewVersion
    override def updateExceptionResolution(
        activity: TrackingActivity,
        index: Int,
        resolvedAt: java.time.Instant,
        resolutionNotes: String
    ): TrackingActivity =
      val current = store(activity.bookingId.value)
      val Right(updated) = current.resolveException(index, resolvedAt, resolutionNotes): @unchecked
      val withNewVersion = TrackingActivity.reconstruct(
        trackingNumber = updated.trackingNumber,
        bookingId = updated.bookingId,
        transportStatus = updated.transportStatus,
        events = updated.events,
        version = updated.version + 1,
        exceptions = updated.exceptions
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

  test("updateStatus: Received を指定すると Receive イベント追記 + status 同期 (US17 / IT6)"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val Right(ta) = svc.assign(AssignTrackingNumberCommand("BK-UPD001")): @unchecked
    val Right(updated) = svc.updateStatus(
      UpdateTrackingStatusCommand(
        trackingNumber = ta.trackingNumber.value,
        status = TrackingStatus.Received,
        locationUnLocode = "JPTYO",
        occurredAt = java.time.Instant.parse("2026-09-10T10:00:00Z")
      )
    ): @unchecked
    updated.transportStatus shouldBe TrackingStatus.Received
    updated.events.size shouldBe 1
    updated.events.head.eventType shouldBe "Receive"

  test("updateStatus: NotReceived など手動更新で許可されない状態は Left (US17)"):
    val svc = new TrackingCommandService(new InMemoryRepo)
    svc
      .updateStatus(
        UpdateTrackingStatusCommand(
          trackingNumber = "TN-000099",
          status = TrackingStatus.NotReceived,
          locationUnLocode = "JPTYO",
          occurredAt = java.time.Instant.parse("2026-09-10T10:00:00Z")
        )
      )
      .isLeft shouldBe true

  test("recordException: Delay 例外を記録すると status=InException に遷移 + 例外履歴に追加 (US19 1.3)"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val Right(ta) = svc.assign(AssignTrackingNumberCommand("BK-EXC001")): @unchecked
    val Right(updated) = svc.recordException(
      RecordExceptionCommand(
        trackingNumber = ta.trackingNumber.value,
        exceptionType = cargotracker.tracking.domain.model.enums.ExceptionType.Delay,
        locationUnLocode = "JPTYO",
        occurredAt = java.time.Instant.parse("2026-09-20T10:00:00Z"),
        description = Some("通関遅延")
      )
    ): @unchecked
    updated.transportStatus shouldBe TrackingStatus.InException
    updated.exceptions should have size 1
    updated.hasActiveException shouldBe true

  test("recordException: Lost 例外は escalationFlag=true (US20)"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val Right(ta) = svc.assign(AssignTrackingNumberCommand("BK-LOST01")): @unchecked
    val Right(updated) = svc.recordException(
      RecordExceptionCommand(
        ta.trackingNumber.value,
        cargotracker.tracking.domain.model.enums.ExceptionType.Lost,
        "USNYC",
        java.time.Instant.parse("2026-09-20T10:00:00Z"),
        Some("コンテナ紛失")
      )
    ): @unchecked
    updated.exceptions.head.escalationFlag shouldBe true

  test("resolveException: 解決すると hasActiveException=false に戻る (US19 1.3)"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val Right(ta) = svc.assign(AssignTrackingNumberCommand("BK-RES001")): @unchecked
    svc.recordException(
      RecordExceptionCommand(
        ta.trackingNumber.value,
        cargotracker.tracking.domain.model.enums.ExceptionType.Delay,
        "JPTYO",
        java.time.Instant.parse("2026-09-20T10:00:00Z"),
        None
      )
    )
    val Right(resolved) = svc.resolveException(
      ResolveExceptionCommand(
        ta.trackingNumber.value,
        index = 0,
        resolvedAt = java.time.Instant.parse("2026-09-20T12:00:00Z"),
        resolutionNotes = "対応完了"
      )
    ): @unchecked
    resolved.hasActiveException shouldBe false
    resolved.exceptions.head.resolutionNotes shouldBe Some("対応完了")

  test("resolveException: 範囲外 index は Left"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val Right(ta) = svc.assign(AssignTrackingNumberCommand("BK-RES002")): @unchecked
    val Left(msg) = svc.resolveException(
      ResolveExceptionCommand(ta.trackingNumber.value, 99, java.time.Instant.parse("2026-09-20T12:00:00Z"), "x")
    ): @unchecked
    msg should include("見つかりません")

  test("updateStatus: appendEvent が OptimisticLockException を投げたら『再読込してください』Left (IT7 0.11 / H8)"):
    val baseRepo = new InMemoryRepo
    val svc = new TrackingCommandService(new TrackingActivityRepository:
      override def nextTrackingNumber(): TrackingNumber = baseRepo.nextTrackingNumber()
      override def findByTrackingNumber(tn: TrackingNumber): Option[TrackingActivity] =
        baseRepo.findByTrackingNumber(tn)
      override def findByBookingId(bid: TrackingBookingId): Option[TrackingActivity] =
        baseRepo.findByBookingId(bid)
      override def save(a: TrackingActivity): Unit = baseRepo.save(a)
      override def appendEvent(
          a: TrackingActivity,
          newEvent: cargotracker.tracking.domain.model.entities.TrackingActivityEvent
      ): TrackingActivity =
        throw cargotracker.shared.domain.OptimisticLockException("TrackingActivity", a.trackingNumber.value)
      override def appendException(
          a: TrackingActivity,
          newException: cargotracker.tracking.domain.model.entities.TrackingExceptionEvent
      ): TrackingActivity =
        throw cargotracker.shared.domain.OptimisticLockException("TrackingActivity", a.trackingNumber.value)
      override def updateExceptionResolution(
          a: TrackingActivity,
          index: Int,
          resolvedAt: java.time.Instant,
          resolutionNotes: String
      ): TrackingActivity =
        throw cargotracker.shared.domain.OptimisticLockException("TrackingActivity", a.trackingNumber.value)
    )
    val Right(ta) = svc.assign(AssignTrackingNumberCommand("BK-UPD002")): @unchecked
    val Left(msg) = svc.updateStatus(
      UpdateTrackingStatusCommand(
        trackingNumber = ta.trackingNumber.value,
        status = TrackingStatus.Received,
        locationUnLocode = "JPTYO",
        occurredAt = java.time.Instant.parse("2026-09-10T10:00:00Z")
      )
    ): @unchecked
    msg should include("再読込してください")
