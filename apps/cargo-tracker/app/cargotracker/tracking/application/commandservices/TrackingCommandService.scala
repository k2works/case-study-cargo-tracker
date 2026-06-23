package cargotracker.tracking.application.commandservices

import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.entities.TrackingActivityEvent
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingLocation, TrackingNumber}

import java.time.Instant
import javax.inject.{Inject, Singleton}

/** 追跡コマンドサービス（US14 + IT5 拡張余地）。
  *
  *   - `assign`: 採番 + `TrackingActivity` 作成。Booking 側の状態遷移は Controller が `BookingCommandService.issueTracking`
  *     を直後に呼んで担保する
  *   - 既存の追跡レコードを持つ予約は冪等成功（既存を返す）
  */
@Singleton
class TrackingCommandService @Inject() (repository: TrackingActivityRepository):

  def assign(command: AssignTrackingNumberCommand): Either[String, TrackingActivity] =
    for
      bookingId <- TrackingBookingId(command.bookingId).left
        .map(_ => "予約 ID が空です")
      existing = repository.findByBookingId(bookingId)
      activity <- existing match
        case Some(ta) => Right(ta) // 冪等
        case None =>
          val tn = repository.nextTrackingNumber()
          TrackingActivity
            .issue(tn, command.bookingId)
            .left
            .map(_ => "追跡レコードの作成に失敗しました")
            .map { ta =>
              repository.save(ta)
              ta
            }
    yield activity

  /** 追跡イベントを追記する（US15）。Handling 側で記録された荷役を Tracking 側履歴に反映する。 */
  def recordEvent(command: RecordTrackingEventCommand): Either[String, TrackingActivity] =
    for
      tn <- TrackingNumber(command.trackingNumber).left
        .map(_ => "追跡番号の形式が不正です")
      activity <- repository
        .findByTrackingNumber(tn)
        .toRight(s"追跡番号 ${command.trackingNumber} が見つかりません")
      event = TrackingActivityEvent(
        eventType = command.eventType,
        eventTime = command.eventTime,
        location = TrackingLocation.of(command.locationUnLocode),
        voyageNumber = command.voyageNumber.filter(_.nonEmpty),
        routeDeviation = command.routeDeviation
      )
      updated <- activity.addEvent(event).left.map {
        case TrackingActivity.OutOfOrder => "追跡イベントの時系列順序が不正です（過去時刻は不可）"
        case _ => "追跡イベントの記録に失敗しました"
      }
    yield repository.appendEvent(updated, event)

/** 追跡番号発行コマンド（US14）。 */
final case class AssignTrackingNumberCommand(bookingId: String)

/** 追跡イベント記録コマンド（US15）。 */
final case class RecordTrackingEventCommand(
    trackingNumber: String,
    eventType: String,
    eventTime: Instant,
    locationUnLocode: String,
    voyageNumber: Option[String],
    routeDeviation: Boolean
)
