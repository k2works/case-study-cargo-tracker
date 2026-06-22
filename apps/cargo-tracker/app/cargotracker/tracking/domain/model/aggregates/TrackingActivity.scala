package cargotracker.tracking.domain.model.aggregates

import cargotracker.tracking.domain.model.entities.TrackingActivityEvent
import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingLocation, TrackingNumber}

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
    events: List[TrackingActivityEvent],
    version: Int
):

  /** イベントを時系列順序を維持して追加する（domain-model.md L760-761 / 不変条件 2）。
    *
    *   - 最終イベントより過去の時刻は拒否（`OutOfOrder`）
    *   - 追加後の `transportStatus` を導出して同時更新する
    */
  def addEvent(event: TrackingActivityEvent): Either[TrackingActivity.Error, TrackingActivity] =
    events.lastOption match
      case Some(last) if event.eventTime.isBefore(last.eventTime) =>
        Left(TrackingActivity.OutOfOrder)
      case _ =>
        val nextEvents = events :+ event
        Right(copy(events = nextEvents, transportStatus = TrackingActivity.deriveStatus(nextEvents)))

  /** 現在の位置（最終イベントの location）。イベントなしなら None。 */
  def currentLocation: Option[TrackingLocation] = events.lastOption.map(_.location)

object TrackingActivity:

  sealed trait Error
  case object EmptyBookingId extends Error
  case object OutOfOrder extends Error

  def issue(trackingNumber: TrackingNumber, bookingId: String): Either[Error, TrackingActivity] =
    TrackingBookingId(bookingId).left
      .map(_ => EmptyBookingId)
      .map(id =>
        new TrackingActivity(
          trackingNumber = trackingNumber,
          bookingId = id,
          transportStatus = TrackingStatus.NotReceived,
          events = Nil,
          version = 0
        )
      )

  /** 永続化からの再構成。 */
  def reconstruct(
      trackingNumber: TrackingNumber,
      bookingId: TrackingBookingId,
      transportStatus: TrackingStatus,
      events: List[TrackingActivityEvent] = Nil,
      version: Int = 0
  ): TrackingActivity =
    new TrackingActivity(trackingNumber, bookingId, transportStatus, events, version)

  /** イベント履歴から TrackingStatus を導出する（不変条件 3 / IT5 US15 導出マトリクス）。 */
  private[aggregates] def deriveStatus(events: List[TrackingActivityEvent]): TrackingStatus =
    events.lastOption match
      case None => TrackingStatus.NotReceived
      case Some(e) =>
        e.eventType match
          case "Receive" => TrackingStatus.Received
          case "Load" => TrackingStatus.Loaded
          case "Unload" => TrackingStatus.Unloaded
          case "Claim" => TrackingStatus.Claimed
          case "Customs" => TrackingStatus.InException
          case _ => TrackingStatus.Unknown

  /** 現状の状態（イベント履歴から導出されたキャッシュ）。 */
  extension (ta: TrackingActivity) def currentStatus: TrackingStatus = ta.transportStatus
