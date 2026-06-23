package cargotracker.tracking.domain.model.aggregates

import cargotracker.tracking.domain.model.entities.TrackingExceptionEvent
import cargotracker.tracking.domain.model.enums.{ExceptionType, TrackingStatus}
import cargotracker.tracking.domain.model.valueobjects.{TrackingLocation, TrackingNumber}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class TrackingExceptionSpec extends AnyFunSuite with Matchers:

  private val tn = TrackingNumber.fromSequence(1)
  private val now = Instant.parse("2026-09-20T10:00:00Z")
  private val later = Instant.parse("2026-09-20T12:00:00Z")
  private val tyo = TrackingLocation.of("JPTYO")

  private def freshActivity: TrackingActivity =
    TrackingActivity.issue(tn, "BK-000001").toOption.get

  test("addException: Delay 例外を追加すると status=InException に遷移する (US19)"):
    val activity = freshActivity
    val updated = activity.addException(
      TrackingExceptionEvent(ExceptionType.Delay, tyo, now, Some("通関遅延"))
    )
    updated.transportStatus shouldBe TrackingStatus.InException
    updated.hasActiveException shouldBe true
    updated.exceptions should have size 1

  test("addException: Lost は escalationFlag=true を強制 (US20)"):
    val activity = freshActivity
    val updated = activity.addException(
      TrackingExceptionEvent(ExceptionType.Lost, tyo, now, Some("コンテナ紛失"), escalationFlag = false)
    )
    updated.exceptions.head.escalationFlag shouldBe true

  test("resolveException: 解決すると status が元の events 由来に戻る"):
    val activity = freshActivity.addException(
      TrackingExceptionEvent(ExceptionType.Damage, tyo, now, Some("水濡れ"))
    )
    val Right(resolved) = activity.resolveException(0, later, "梱包替えで対応"): @unchecked
    resolved.transportStatus shouldBe TrackingStatus.NotReceived // events 空なので NotReceived に戻る
    resolved.hasActiveException shouldBe false
    resolved.exceptions.head.resolvedAt shouldBe Some(later)
    resolved.exceptions.head.resolutionNotes shouldBe Some("梱包替えで対応")

  test("resolveException: 範囲外 index は ExceptionNotFound"):
    val activity = freshActivity
    activity.resolveException(0, later, "x") shouldBe Left(TrackingActivity.ExceptionNotFound)

  test("addException: 複数件のうち 1 件解決でもアクティブが残れば InException 維持"):
    val activity = freshActivity
      .addException(TrackingExceptionEvent(ExceptionType.Delay, tyo, now))
      .addException(TrackingExceptionEvent(ExceptionType.Damage, tyo, now))
    val Right(partial) = activity.resolveException(0, later, "Delay 対応済"): @unchecked
    partial.hasActiveException shouldBe true
    partial.transportStatus shouldBe TrackingStatus.InException
