package cargotracker.tracking.domain.model.aggregates

import cargotracker.tracking.domain.model.entities.{TrackingActivityEvent, TrackingExceptionEvent}
import cargotracker.tracking.domain.model.enums.{ExceptionType, TrackingStatus}
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingLocation, TrackingNumber}

import java.time.Instant

/** 追跡レコード（Tracking Context 集約ルート）。
  *
  *   - IT5 US14 で骨格を作成し、`TrackingActivityEvent` / `TrackingExceptionEvent` は IT5 US15 / IT7 で追加
  *   - 採番後の `trackingNumber` は変更不可（不変条件）
  *   - `transportStatus` は Read Model 用のキャッシュ（書込時に同期）。`currentStatus()` がイベント履歴から導出した結果と一致する
  *   - IT7 US19/US20: アクティブな `exceptions` がある場合 `transportStatus = InException`
  */
final case class TrackingActivity private (
    trackingNumber: TrackingNumber,
    bookingId: TrackingBookingId,
    transportStatus: TrackingStatus,
    events: List[TrackingActivityEvent],
    exceptions: List[TrackingExceptionEvent],
    version: Int
):
  // 不変条件 3 (H7): transportStatus は events 履歴 + exceptions から導出した結果と一致する
  require(
    transportStatus == TrackingActivity.deriveStatus(events, exceptions),
    s"transportStatus($transportStatus) does not match derived from events/exceptions"
  )

  /** イベントを時系列順序を維持して追加する（domain-model.md L760-761 / 不変条件 2）。 */
  def addEvent(event: TrackingActivityEvent): Either[TrackingActivity.Error, TrackingActivity] =
    events.lastOption match
      case Some(last) if event.eventTime.isBefore(last.eventTime) =>
        Left(TrackingActivity.OutOfOrder)
      case _ =>
        val nextEvents = events :+ event
        Right(copy(events = nextEvents, transportStatus = TrackingActivity.deriveStatus(nextEvents, exceptions)))

  /** 例外イベントを追加する（IT7 US19/US20）。`Lost` は escalationFlag を true に強制。 */
  def addException(exception: TrackingExceptionEvent): TrackingActivity =
    val normalized =
      if exception.exceptionType == ExceptionType.Lost then exception.copy(escalationFlag = true)
      else exception
    val nextExceptions = exceptions :+ normalized
    copy(exceptions = nextExceptions, transportStatus = TrackingActivity.deriveStatus(events, nextExceptions))

  /** 指定された例外を解決済にする（resolvedAt + resolutionNotes を埋める）。 */
  def resolveException(
      index: Int,
      resolvedAt: Instant,
      resolutionNotes: String
  ): Either[TrackingActivity.Error, TrackingActivity] =
    if index < 0 || index >= exceptions.size then Left(TrackingActivity.ExceptionNotFound)
    else
      val resolved = exceptions(index).copy(
        resolvedAt = Some(resolvedAt),
        resolutionNotes = Some(resolutionNotes)
      )
      val nextExceptions = exceptions.updated(index, resolved)
      Right(copy(exceptions = nextExceptions, transportStatus = TrackingActivity.deriveStatus(events, nextExceptions)))

  /** アクティブな例外があるか。 */
  def hasActiveException: Boolean = exceptions.exists(_.isActive)

  /** 現在の位置（最終イベントの location）。イベントなしなら None。 */
  def currentLocation: Option[TrackingLocation] = events.lastOption.map(_.location)

object TrackingActivity:

  sealed trait Error
  case object EmptyBookingId extends Error
  case object OutOfOrder extends Error
  case object ExceptionNotFound extends Error

  def issue(trackingNumber: TrackingNumber, bookingId: String): Either[Error, TrackingActivity] =
    TrackingBookingId(bookingId).left
      .map(_ => EmptyBookingId)
      .map(id =>
        new TrackingActivity(
          trackingNumber = trackingNumber,
          bookingId = id,
          transportStatus = TrackingStatus.NotReceived,
          events = Nil,
          exceptions = Nil,
          version = 0
        )
      )

  /** 永続化からの再構成。 */
  def reconstruct(
      trackingNumber: TrackingNumber,
      bookingId: TrackingBookingId,
      transportStatus: TrackingStatus,
      events: List[TrackingActivityEvent] = Nil,
      version: Int = 0,
      exceptions: List[TrackingExceptionEvent] = Nil
  ): TrackingActivity =
    new TrackingActivity(trackingNumber, bookingId, transportStatus, events, exceptions, version)

  /** イベント履歴 + アクティブ例外から TrackingStatus を導出する（不変条件 3 / IT7 拡張）。 */
  private[aggregates] def deriveStatus(
      events: List[TrackingActivityEvent],
      exceptions: List[TrackingExceptionEvent]
  ): TrackingStatus =
    if exceptions.exists(_.isActive) then TrackingStatus.InException
    else
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
