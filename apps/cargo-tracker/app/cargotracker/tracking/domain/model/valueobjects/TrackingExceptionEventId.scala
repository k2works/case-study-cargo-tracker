package cargotracker.tracking.domain.model.valueobjects

/** 追跡例外イベントの永続化 PK (IT8 0.5 / H5 解消)。
  *
  *   - DB の `tracking_exception_event.id` (BIGSERIAL) を型安全に扱う
  *   - 採番は Repository (INSERT 後の RETURNING / SELECT 再ロード) が行う。集約内では `Option[TrackingExceptionEventId]` として 未保存 (None) /
  *     保存済 (Some) を区別する
  *   - これにより `updateExceptionResolution` を `(exception_type, occurred_at)` 複合キーではなく PK 直接更新に変更し、 同一 type/time
  *     競合のリスクと「ドメインは Green だが本番で取り出せない」リスクを構造的に排除する
  */
opaque type TrackingExceptionEventId = Long

object TrackingExceptionEventId:
  def apply(value: Long): TrackingExceptionEventId = value

  extension (id: TrackingExceptionEventId) def value: Long = id
