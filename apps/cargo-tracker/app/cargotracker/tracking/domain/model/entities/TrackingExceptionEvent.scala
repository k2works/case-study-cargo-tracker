package cargotracker.tracking.domain.model.entities

import cargotracker.tracking.domain.model.enums.ExceptionType
import cargotracker.tracking.domain.model.valueobjects.{TrackingExceptionEventId, TrackingLocation}

import java.time.Instant

/** 追跡例外イベント（US19 遅延 / US20 破損・紛失 / IT7、IT8 0.5 で id 追加）。
  *
  *   - `id`: 永続化 PK (`TrackingExceptionEventId`)。未保存時は None、保存後は Some。 IT8 0.5 (H5) で複合キー UPDATE を PK 直接更新に切り替えるため導入
  *   - `exceptionType`: 例外種別 (Delay / Damage / Lost / CustomsHold)
  *   - `location`: 発生場所
  *   - `occurredAt`: 発生時刻
  *   - `description`: 詳細記述（任意）
  *   - `escalationFlag`: 管理職エスカレーション要否 (Lost は強制 true)
  *   - `resolvedAt`: 対応完了時刻 (None = 未対応)
  *   - `resolutionNotes`: 対応報告内容
  */
final case class TrackingExceptionEvent(
    exceptionType: ExceptionType,
    location: TrackingLocation,
    occurredAt: Instant,
    description: Option[String] = None,
    escalationFlag: Boolean = false,
    resolvedAt: Option[Instant] = None,
    resolutionNotes: Option[String] = None,
    id: Option[TrackingExceptionEventId] = None
):
  def isResolved: Boolean = resolvedAt.isDefined
  def isActive: Boolean = !isResolved
