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
  def save(activity: TrackingActivity): Unit
