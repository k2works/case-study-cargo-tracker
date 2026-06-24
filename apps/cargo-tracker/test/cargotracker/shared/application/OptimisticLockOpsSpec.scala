package cargotracker.shared.application

import cargotracker.shared.domain.OptimisticLockException
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** IT8 タスク 0.1 (H1): 楽観ロック try/catch を共通ヘルパ抽出した withOptimisticLock の単体テスト。
  *
  * TrackingCommandService / BillingCommandService / 他コマンドサービスで重複していた 「try repository操作 catch OptimisticLockException →
  * Left("他のユーザーが更新したため再読込してください") / NonFatal → Left(s"${label}の保存に失敗しました")」パターンを置き換える。
  */
class OptimisticLockOpsSpec extends AnyWordSpec with Matchers with EitherValues:

  "withOptimisticLock" should:
    "ブロックが正常終了したら Right で結果を返す" in:
      val result = OptimisticLockOps.withOptimisticLock("追跡イベント")(42)
      result.value shouldBe 42

    "OptimisticLockException を捕捉して固定メッセージを Left で返す" in:
      val result = OptimisticLockOps.withOptimisticLock("追跡イベント") {
        throw OptimisticLockException("TrackingActivity", "TN-000001")
      }
      result.left.value shouldBe "他のユーザーが更新したため再読込してください"

    "NonFatal な例外を捕捉して label を含む保存失敗メッセージを Left で返す" in:
      val result = OptimisticLockOps.withOptimisticLock("追跡例外") {
        throw new RuntimeException("DB connection lost")
      }
      result.left.value shouldBe "追跡例外の保存に失敗しました"

    "label が異なれば失敗メッセージの主語も切り替わる" in:
      val result = OptimisticLockOps.withOptimisticLock("例外対応報告") {
        throw new IllegalStateException("boom")
      }
      result.left.value shouldBe "例外対応報告の保存に失敗しました"

    "致命的な例外 (Fatal) は再 throw する（NonFatal でないため）" in:
      assertThrows[OutOfMemoryError]:
        OptimisticLockOps.withOptimisticLock("追跡イベント") {
          throw new OutOfMemoryError("simulated fatal")
        }
