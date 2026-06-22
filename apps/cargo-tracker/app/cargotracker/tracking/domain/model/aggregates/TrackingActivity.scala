package cargotracker.tracking.domain.model.aggregates

import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingNumber}

/** 追跡レコード（Tracking Context 集約ルート）。
  *
  *   - IT5 US14 で骨格を作成し、`TrackingActivityEvent` / `TrackingExceptionEvent` は IT5 US15 / IT7 で追加
  *   - 採番後の `trackingNumber` は変更不可（不変条件）
  *   - `transportStatus` は Read Model 用のキャッシュ（書込時に同期）。`currentStatus()` がイベント履歴から導出した結果と一致する
  *   - 詳細は ADR 0010 参照
  */
final case class TrackingActivity private (
    trackingNumber: TrackingNumber,
    bookingId: TrackingBookingId,
    transportStatus: TrackingStatus,
    version: Int
)

object TrackingActivity:

  sealed trait Error
  case object EmptyBookingId extends Error

  def issue(trackingNumber: TrackingNumber, bookingId: String): Either[Error, TrackingActivity] =
    TrackingBookingId(bookingId).left
      .map(_ => EmptyBookingId)
      .map(id =>
        new TrackingActivity(
          trackingNumber = trackingNumber,
          bookingId = id,
          transportStatus = TrackingStatus.NotReceived,
          version = 0
        )
      )

  /** 永続化からの再構成。 */
  def reconstruct(
      trackingNumber: TrackingNumber,
      bookingId: TrackingBookingId,
      transportStatus: TrackingStatus,
      version: Int
  ): TrackingActivity =
    new TrackingActivity(trackingNumber, bookingId, transportStatus, version)

  /** 現状（IT5 US14 段階）の状態。IT5 US15 で `addEvent` 経由でイベント履歴を持つようになる。 */
  extension (ta: TrackingActivity) def currentStatus: TrackingStatus = ta.transportStatus
