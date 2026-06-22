package cargotracker.tracking.domain.model.repositories

import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingNumber}

/** Tracking Context のリポジトリポート（ヘキサゴナル）。
  *
  * 採番は `nextTrackingNumber` で行う（実装は `BIGSERIAL` を `TN-NNNNNN` に整形）。
  */
trait TrackingActivityRepository:
  def nextTrackingNumber(): TrackingNumber
  def findByTrackingNumber(trackingNumber: TrackingNumber): Option[TrackingActivity]
  def findByBookingId(bookingId: TrackingBookingId): Option[TrackingActivity]

  /** 集約ルートの永続化。新規 INSERT または `version` の差分更新のみを行う（events は appendEvent 経由）。 */
  def save(activity: TrackingActivity): Unit

  /** 集約配下の新規イベント 1 件を追記し、`transportStatus` と `version` を同時更新する（楽観ロック）。 */
  def appendEvent(
      activity: TrackingActivity,
      newEvent: cargotracker.tracking.domain.model.entities.TrackingActivityEvent
  ): Unit
