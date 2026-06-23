package cargotracker.tracking.domain.model.entities

import cargotracker.tracking.domain.model.enums.ExceptionType
import cargotracker.tracking.domain.model.valueobjects.TrackingLocation

import java.time.Instant

/** 追跡例外イベント（US19 遅延 / US20 破損・紛失 / IT7）。
  *
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
    resolutionNotes: Option[String] = None
):
  def isResolved: Boolean = resolvedAt.isDefined
  def isActive: Boolean = !isResolved
