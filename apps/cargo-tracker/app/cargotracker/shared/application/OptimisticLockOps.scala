package cargotracker.shared.application

import cargotracker.shared.domain.OptimisticLockException

import scala.util.control.NonFatal

/** 楽観ロック対応ヘルパ (IT8 タスク 0.1 / H1 解消)。
  *
  *   - 永続化操作を `withOptimisticLock(label) { repository.update(...) }` で囲うと、 `OptimisticLockException` は固定メッセージで
  *     Left、`NonFatal` な例外は `${label}の保存に失敗しました` で Left に変換される。
  *   - 致命的例外 (`OutOfMemoryError` 等) は捕捉せず再 throw する。
  *   - TrackingCommandService 3 箇所 + 今後追加されるコマンドサービス (Billing 等) で再利用する。
  */
object OptimisticLockOps:

  /** ブロックを実行し、楽観ロック競合 / 永続化失敗を `Either[String, A]` に変換する。
    *
    * @param label
    *   失敗時メッセージの主語 (例: "追跡イベント" / "追跡例外" / "例外対応報告" / "請求書" / "入金")
    * @param block
    *   永続化操作（成功時に A を返す副作用ブロック）
    */
  def withOptimisticLock[A](label: String)(block: => A): Either[String, A] =
    try Right(block)
    catch
      case _: OptimisticLockException => Left("他のユーザーが更新したため再読込してください")
      case NonFatal(_) => Left(s"${label}の保存に失敗しました")
