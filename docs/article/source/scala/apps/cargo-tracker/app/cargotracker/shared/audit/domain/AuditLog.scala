package cargotracker.shared.audit.domain

import java.time.Instant

/** システム監査ログエンティティ (IT9 US30 / ISO 27001 対応)。
  *
  *   - 不変記録: insert 後の after フィールド更新は禁止 (AuditLogPort に update メソッド未提供)
  *   - operator は AuthenticatedRequest.user.id から自動取得 (Controller 層で設定)
  *   - before / after は集約のシリアライズ JSON (任意、空でも可)
  */
final case class AuditLog(
    id: Option[AuditLogId],
    operator: String,
    action: AuditAction,
    targetType: String,
    targetId: String,
    before: Option[String],
    after: Option[String],
    occurredAt: Instant
)

/** 監査ログ ID (永続化 PK)。 */
opaque type AuditLogId = Long
object AuditLogId:
  def apply(value: Long): AuditLogId = value
  extension (id: AuditLogId) def value: Long = id

/** 監査対象の操作種別 (IT9 US30、CHECK 制約と一致させる)。 */
enum AuditAction:
  case CancelExceptionResolution // IT8 H9
  case AppendResolutionComment // IT8 H9
  case IssuePayment // IT8 US23
  case ConfirmPayment // IT8 US23
  case Refund // IT9 R4
  case ImportPaymentsBatch // IT9 US29

object AuditAction:
  def fromName(name: String): Option[AuditAction] = values.find(_.toString == name)
