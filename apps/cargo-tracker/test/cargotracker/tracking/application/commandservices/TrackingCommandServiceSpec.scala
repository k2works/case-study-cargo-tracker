package cargotracker.tracking.application.commandservices

import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingNumber}
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** IT8 タスク 0.10 (H12 解消): `val Right(x) = ...: @unchecked` パターンを EitherValues `.value` / `.left.value`
  * に統一し、テスト失敗時のスタックトレースで「期待値が Right だったか Left だったか」を明示できるようにする。
  */
class TrackingCommandServiceSpec extends AnyFunSuite with Matchers with EitherValues:

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
      val updated = current.resolveException(index, resolvedAt, resolutionNotes).value
      bumpAndStore(updated)
    override def clearExceptionResolution(activity: TrackingActivity, index: Int): TrackingActivity =
      val current = store(activity.bookingId.value)
      val updated = current.cancelExceptionResolution(index).value
      bumpAndStore(updated)
    override def updateExceptionNotes(
        activity: TrackingActivity,
        index: Int,
        mergedNotes: String
    ): TrackingActivity =
      val current = store(activity.bookingId.value)
      val target = current.exceptions(index)
      val nextExceptions = current.exceptions.updated(index, target.copy(resolutionNotes = Some(mergedNotes)))
      bumpAndStore(
        TrackingActivity.reconstruct(
          trackingNumber = current.trackingNumber,
          bookingId = current.bookingId,
          transportStatus = current.transportStatus,
          events = current.events,
          version = current.version,
          exceptions = nextExceptions
        )
      )
    private def bumpAndStore(updated: TrackingActivity): TrackingActivity =
      val withNewVersion = TrackingActivity.reconstruct(
        trackingNumber = updated.trackingNumber,
        bookingId = updated.bookingId,
        transportStatus = updated.transportStatus,
        events = updated.events,
        version = updated.version + 1,
        exceptions = updated.exceptions
      )
      store(updated.bookingId.value) = withNewVersion
      withNewVersion

  test("assign: 新規予約に対して採番し TrackingActivity を初期化（NotReceived）"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val ta = svc.assign(AssignTrackingNumberCommand("BK-000001")).value
    ta.trackingNumber.value shouldBe "TN-000001"
    ta.bookingId.value shouldBe "BK-000001"
    ta.transportStatus shouldBe TrackingStatus.NotReceived
    repo.store.size shouldBe 1

  test("assign: 同一予約への 2 回目呼出は冪等成功（既存番号を返す）"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val first = svc.assign(AssignTrackingNumberCommand("BK-000002")).value
    val second = svc.assign(AssignTrackingNumberCommand("BK-000002")).value
    first.trackingNumber shouldBe second.trackingNumber
    repo.store.size shouldBe 1

  test("assign: 空の予約 ID は Left"):
    val svc = new TrackingCommandService(new InMemoryRepo)
    svc.assign(AssignTrackingNumberCommand("")).isLeft shouldBe true

  test("updateStatus: Received を指定すると Receive イベント追記 + status 同期 (US17 / IT6)"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val ta = svc.assign(AssignTrackingNumberCommand("BK-UPD001")).value
    val updated = svc
      .updateStatus(
        UpdateTrackingStatusCommand(
          trackingNumber = ta.trackingNumber.value,
          status = TrackingStatus.Received,
          locationUnLocode = "JPTYO",
          occurredAt = java.time.Instant.parse("2026-09-10T10:00:00Z")
        )
      )
      .value
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
    val ta = svc.assign(AssignTrackingNumberCommand("BK-EXC001")).value
    val updated = svc
      .recordException(
        RecordExceptionCommand(
          trackingNumber = ta.trackingNumber.value,
          exceptionType = cargotracker.tracking.domain.model.enums.ExceptionType.Delay,
          locationUnLocode = "JPTYO",
          occurredAt = java.time.Instant.parse("2026-09-20T10:00:00Z"),
          description = Some("通関遅延")
        )
      )
      .value
    updated.transportStatus shouldBe TrackingStatus.InException
    updated.exceptions should have size 1
    updated.hasActiveException shouldBe true

  test("recordException: Lost 例外は escalationFlag=true (US20)"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val ta = svc.assign(AssignTrackingNumberCommand("BK-LOST01")).value
    val updated = svc
      .recordException(
        RecordExceptionCommand(
          ta.trackingNumber.value,
          cargotracker.tracking.domain.model.enums.ExceptionType.Lost,
          "USNYC",
          java.time.Instant.parse("2026-09-20T10:00:00Z"),
          Some("コンテナ紛失")
        )
      )
      .value
    updated.exceptions.head.escalationFlag shouldBe true

  test("resolveException: 解決すると hasActiveException=false に戻る (US19 1.3)"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val ta = svc.assign(AssignTrackingNumberCommand("BK-RES001")).value
    svc.recordException(
      RecordExceptionCommand(
        ta.trackingNumber.value,
        cargotracker.tracking.domain.model.enums.ExceptionType.Delay,
        "JPTYO",
        java.time.Instant.parse("2026-09-20T10:00:00Z"),
        None
      )
    )
    val resolved = svc
      .resolveException(
        ResolveExceptionCommand(
          ta.trackingNumber.value,
          index = 0,
          resolvedAt = java.time.Instant.parse("2026-09-20T12:00:00Z"),
          resolutionNotes = "対応完了"
        )
      )
      .value
    resolved.hasActiveException shouldBe false
    resolved.exceptions.head.resolutionNotes shouldBe Some("対応完了")

  // IT8 0.7 (H9): 対応取消し + 補足コメント追記

  test("cancelExceptionResolution: 解決済例外を取消すと未解決状態に戻る (IT8 0.7 / H9)"):
    val svc = new TrackingCommandService(new InMemoryRepo)
    val ta = svc.assign(AssignTrackingNumberCommand("BK-CXL01")).value
    svc.recordException(
      RecordExceptionCommand(
        ta.trackingNumber.value,
        cargotracker.tracking.domain.model.enums.ExceptionType.Delay,
        "JPTYO",
        java.time.Instant.parse("2026-09-20T10:00:00Z"),
        Some("通関遅延")
      )
    )
    svc.resolveException(
      ResolveExceptionCommand(ta.trackingNumber.value, 0, java.time.Instant.parse("2026-09-20T12:00:00Z"), "解決")
    )
    val cancelled = svc.cancelExceptionResolution(CancelExceptionResolutionCommand(ta.trackingNumber.value, 0)).value
    cancelled.exceptions.head.resolvedAt shouldBe None
    cancelled.exceptions.head.resolutionNotes shouldBe None
    cancelled.hasActiveException shouldBe true

  test("cancelExceptionResolution: 未解決例外への取消しは Left (IT8 0.7 / H9)"):
    val svc = new TrackingCommandService(new InMemoryRepo)
    val ta = svc.assign(AssignTrackingNumberCommand("BK-CXL02")).value
    svc.recordException(
      RecordExceptionCommand(
        ta.trackingNumber.value,
        cargotracker.tracking.domain.model.enums.ExceptionType.Delay,
        "JPTYO",
        java.time.Instant.parse("2026-09-20T10:00:00Z"),
        None
      )
    )
    val msg = svc.cancelExceptionResolution(CancelExceptionResolutionCommand(ta.trackingNumber.value, 0)).left.value
    msg should include("解決されていません")

  test("appendResolutionComment: コメントを追記すると resolutionNotes に改行区切りで連結される (IT8 0.7 / H9)"):
    val svc = new TrackingCommandService(new InMemoryRepo)
    val ta = svc.assign(AssignTrackingNumberCommand("BK-CMT01")).value
    svc.recordException(
      RecordExceptionCommand(
        ta.trackingNumber.value,
        cargotracker.tracking.domain.model.enums.ExceptionType.Damage,
        "USNYC",
        java.time.Instant.parse("2026-09-20T10:00:00Z"),
        Some("初期報告")
      )
    )
    svc.resolveException(
      ResolveExceptionCommand(ta.trackingNumber.value, 0, java.time.Instant.parse("2026-09-20T12:00:00Z"), "補償交渉中")
    )
    val updated = svc
      .appendResolutionComment(AppendResolutionCommentCommand(ta.trackingNumber.value, 0, "補償申請受領"))
      .value
    updated.exceptions.head.resolutionNotes.get should include("補償交渉中")
    updated.exceptions.head.resolutionNotes.get should include("補償申請受領")
    updated.exceptions.head.resolutionNotes.get should include("---") // 区切り

  test("appendResolutionComment: 空コメントは Left (IT8 0.7 / H9)"):
    val svc = new TrackingCommandService(new InMemoryRepo)
    val ta = svc.assign(AssignTrackingNumberCommand("BK-CMT02")).value
    svc.recordException(
      RecordExceptionCommand(
        ta.trackingNumber.value,
        cargotracker.tracking.domain.model.enums.ExceptionType.Delay,
        "JPTYO",
        java.time.Instant.parse("2026-09-20T10:00:00Z"),
        None
      )
    )
    val msg = svc.appendResolutionComment(AppendResolutionCommentCommand(ta.trackingNumber.value, 0, "   ")).left.value
    msg should include("必須")

  test("resolveException: 範囲外 index は Left"):
    val repo = new InMemoryRepo
    val svc = new TrackingCommandService(repo)
    val ta = svc.assign(AssignTrackingNumberCommand("BK-RES002")).value
    val msg = svc
      .resolveException(
        ResolveExceptionCommand(ta.trackingNumber.value, 99, java.time.Instant.parse("2026-09-20T12:00:00Z"), "x")
      )
      .left
      .value
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
      override def clearExceptionResolution(a: TrackingActivity, index: Int): TrackingActivity =
        throw cargotracker.shared.domain.OptimisticLockException("TrackingActivity", a.trackingNumber.value)
      override def updateExceptionNotes(a: TrackingActivity, index: Int, mergedNotes: String): TrackingActivity =
        throw cargotracker.shared.domain.OptimisticLockException("TrackingActivity", a.trackingNumber.value)
    )
    val ta = svc.assign(AssignTrackingNumberCommand("BK-UPD002")).value
    val msg = svc
      .updateStatus(
        UpdateTrackingStatusCommand(
          trackingNumber = ta.trackingNumber.value,
          status = TrackingStatus.Received,
          locationUnLocode = "JPTYO",
          occurredAt = java.time.Instant.parse("2026-09-10T10:00:00Z")
        )
      )
      .left
      .value
    msg should include("再読込してください")
