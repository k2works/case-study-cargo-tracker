package cargotracker.shared.audit.domain

import java.time.Instant

/** 監査ログ記録 / 検索のための出力 Port (IT9 US30 / ADR 0022 予定)。
  *
  *   - record: 新規ログを INSERT (UPDATE / DELETE は提供しない = 不変記録)
  *   - findByFilter: 一覧画面 (/admin/audit-logs) のためのフィルタ検索
  *   - findById: 詳細画面 (/admin/audit-logs/:id) のため
  *
  * shared.audit に配置することで全 Context (TrackingController / InvoiceController 等) からアクセス可能 (shared kernel)。
  */
trait AuditLogPort:

  /** 新規監査ログを記録する (insert のみ)。 */
  def record(
      operator: String,
      action: AuditAction,
      targetType: String,
      targetId: String,
      before: Option[String],
      after: Option[String]
  ): Either[String, AuditLogId]

  /** フィルタ条件で監査ログを検索する (日付範囲 / アクター / 操作種別、いずれも省略可、occurredAt DESC 順)。 */
  def findByFilter(filter: AuditLogFilter): Seq[AuditLog]

  /** ID 直接検索 (詳細画面用)。 */
  def findById(id: AuditLogId): Option[AuditLog]

/** 監査ログ検索フィルタ (全項目省略可)。 */
final case class AuditLogFilter(
    fromOccurredAt: Option[Instant] = None,
    toOccurredAt: Option[Instant] = None,
    operator: Option[String] = None,
    action: Option[AuditAction] = None,
    limit: Int = 100
)
