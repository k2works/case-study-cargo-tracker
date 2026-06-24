package cargotracker.tracking.domain.model.aggregates

import cargotracker.tracking.domain.model.entities.TrackingExceptionEvent
import cargotracker.tracking.domain.model.enums.{ExceptionType, TrackingStatus}
import cargotracker.tracking.domain.model.valueobjects.{TrackingLocation, TrackingNumber}
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

/** IT8 タスク 0.6 (H7 / M6): TrackingException 同値クラス代表値テストを拡充。
  *
  *   - CustomsHold → InException 遷移
  *   - Damage デフォルト escalationFlag=false （Lost のみが強制 true である事実の対称テスト）
  *   - 解決済例外を再度 resolveException した場合の **上書き許容** 仕様（業務上は 0.7 で「取消し動線」を別途追加するため、本メソッドは冪等な上書きに留める）
  */
class TrackingExceptionSpec extends AnyFunSuite with Matchers with EitherValues:

  private val tn = TrackingNumber.fromSequence(1)
  private val now = Instant.parse("2026-09-20T10:00:00Z")
  private val later = Instant.parse("2026-09-20T12:00:00Z")
  private val muchLater = Instant.parse("2026-09-21T15:00:00Z")
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

  test("addException: Damage 例外も同様に status=InException に遷移する (US20 / 同値クラス)"):
    val activity = freshActivity
    val updated = activity.addException(
      TrackingExceptionEvent(ExceptionType.Damage, tyo, now, Some("水濡れ"))
    )
    updated.transportStatus shouldBe TrackingStatus.InException
    updated.hasActiveException shouldBe true

  test("addException: CustomsHold 例外も status=InException に遷移する (同値クラス / IT8 0.6)"):
    val activity = freshActivity
    val updated = activity.addException(
      TrackingExceptionEvent(ExceptionType.CustomsHold, tyo, now, Some("輸入書類不備"))
    )
    updated.transportStatus shouldBe TrackingStatus.InException
    updated.hasActiveException shouldBe true
    updated.exceptions.head.exceptionType shouldBe ExceptionType.CustomsHold

  test("addException: Lost は escalationFlag=true を強制 (US20)"):
    val activity = freshActivity
    val updated = activity.addException(
      TrackingExceptionEvent(ExceptionType.Lost, tyo, now, Some("コンテナ紛失"), escalationFlag = false)
    )
    updated.exceptions.head.escalationFlag shouldBe true

  test("addException: Damage デフォルトは escalationFlag=false (Lost のみが強制 true の対称テスト / IT8 0.6 M6)"):
    val activity = freshActivity
    val updated = activity.addException(
      TrackingExceptionEvent(ExceptionType.Damage, tyo, now, Some("水濡れ"))
    )
    updated.exceptions.head.escalationFlag shouldBe false

  test("addException: Delay デフォルトも escalationFlag=false (IT8 0.6 M6 同値クラス)"):
    val activity = freshActivity
    val updated = activity.addException(
      TrackingExceptionEvent(ExceptionType.Delay, tyo, now)
    )
    updated.exceptions.head.escalationFlag shouldBe false

  test("addException: CustomsHold デフォルトも escalationFlag=false (IT8 0.6 M6 同値クラス)"):
    val activity = freshActivity
    val updated = activity.addException(
      TrackingExceptionEvent(ExceptionType.CustomsHold, tyo, now)
    )
    updated.exceptions.head.escalationFlag shouldBe false

  test("resolveException: 解決すると status が元の events 由来に戻る"):
    val activity = freshActivity.addException(
      TrackingExceptionEvent(ExceptionType.Damage, tyo, now, Some("水濡れ"))
    )
    val resolved = activity.resolveException(0, later, "梱包替えで対応").value
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
    val partial = activity.resolveException(0, later, "Delay 対応済").value
    partial.hasActiveException shouldBe true
    partial.transportStatus shouldBe TrackingStatus.InException

  test("resolveException: 解決済例外を再度 resolveException すると上書き許容（冪等な上書き仕様 / IT8 0.6 H7）"):
    val activity = freshActivity.addException(
      TrackingExceptionEvent(ExceptionType.Damage, tyo, now, Some("初回判定"))
    )
    val firstResolved = activity.resolveException(0, later, "梱包替えで対応").value
    firstResolved.exceptions.head.resolvedAt shouldBe Some(later)
    firstResolved.exceptions.head.resolutionNotes shouldBe Some("梱包替えで対応")

    // 再度 resolveException した場合: AlreadyResolved エラーは返さず、新しい resolvedAt / resolutionNotes に上書きされる
    val secondResolved = firstResolved.resolveException(0, muchLater, "再対応 - 補償申請完了").value
    secondResolved.exceptions.head.resolvedAt shouldBe Some(muchLater)
    secondResolved.exceptions.head.resolutionNotes shouldBe Some("再対応 - 補償申請完了")
    secondResolved.hasActiveException shouldBe false
