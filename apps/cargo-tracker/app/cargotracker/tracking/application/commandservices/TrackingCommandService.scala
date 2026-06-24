package cargotracker.tracking.application.commandservices

import cargotracker.shared.application.OptimisticLockOps.withOptimisticLock
import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.entities.TrackingActivityEvent
import cargotracker.tracking.domain.model.enums.TrackingStatus
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

  /** 状態を手動更新する（US17 / IT6）。
    *
    *   - 指定 status に対応する eventType を導出してイベント追記する
    *   - 楽観ロック対応 (appendEvent が衝突時に OptimisticLockException を投げる)
    */
  def updateStatus(command: UpdateTrackingStatusCommand): Either[String, TrackingActivity] =
    val result = for
      tn <- TrackingNumber(command.trackingNumber).left.map(_ => "追跡番号の形式が不正です")
      eventType <- TrackingCommandService.eventTypeFor(command.status)
      activity <- repository
        .findByTrackingNumber(tn)
        .toRight(s"追跡番号 ${command.trackingNumber} が見つかりません")
      event = TrackingActivityEvent(
        eventType = eventType,
        eventTime = command.occurredAt,
        location = TrackingLocation.of(command.locationUnLocode),
        voyageNumber = None,
        routeDeviation = false
      )
      updated <- activity.addEvent(event).left.map {
        case TrackingActivity.OutOfOrder => "追跡イベントの時系列順序が不正です（過去時刻は不可）"
        case _ => "追跡イベントの記録に失敗しました"
      }
    yield (updated, event)
    result.flatMap { case (updated, event) =>
      withOptimisticLock("追跡イベント")(repository.appendEvent(updated, event))
    }

  /** 追跡例外を記録する（US19 遅延 / US20 破損・紛失）。楽観ロック付き。 */
  def recordException(command: RecordExceptionCommand): Either[String, TrackingActivity] =
    for
      tn <- TrackingNumber(command.trackingNumber).left.map(_ => "追跡番号の形式が不正です")
      activity <- repository
        .findByTrackingNumber(tn)
        .toRight(s"追跡番号 ${command.trackingNumber} が見つかりません")
      exception = cargotracker.tracking.domain.model.entities.TrackingExceptionEvent(
        exceptionType = command.exceptionType,
        location = cargotracker.tracking.domain.model.valueobjects.TrackingLocation.of(command.locationUnLocode),
        occurredAt = command.occurredAt,
        description = command.description.filter(_.nonEmpty)
      )
      updated = activity.addException(exception)
      result <- withOptimisticLock("追跡例外")(repository.appendException(updated, updated.exceptions.last))
    yield result

  /** 追跡例外の対応報告（resolvedAt + resolutionNotes 設定）。 */
  def resolveException(command: ResolveExceptionCommand): Either[String, TrackingActivity] =
    for
      tn <- TrackingNumber(command.trackingNumber).left.map(_ => "追跡番号の形式が不正です")
      activity <- repository
        .findByTrackingNumber(tn)
        .toRight(s"追跡番号 ${command.trackingNumber} が見つかりません")
      updated <- activity.resolveException(command.index, command.resolvedAt, command.resolutionNotes).left.map {
        case TrackingActivity.ExceptionNotFound => s"指定された例外 (#${command.index}) が見つかりません"
        case _ => "例外の対応報告に失敗しました"
      }
      result <- withOptimisticLock("例外対応報告"):
        repository.updateExceptionResolution(updated, command.index, command.resolvedAt, command.resolutionNotes)
    yield result

  /** 例外対応を取消す (IT8 0.7 / H9): resolvedAt / resolutionNotes をクリアし、再対応待ち状態に戻す。 */
  def cancelExceptionResolution(command: CancelExceptionResolutionCommand): Either[String, TrackingActivity] =
    for
      tn <- TrackingNumber(command.trackingNumber).left.map(_ => "追跡番号の形式が不正です")
      activity <- repository
        .findByTrackingNumber(tn)
        .toRight(s"追跡番号 ${command.trackingNumber} が見つかりません")
      updated <- activity.cancelExceptionResolution(command.index).left.map {
        case TrackingActivity.ExceptionNotFound => s"指定された例外 (#${command.index}) が見つかりません"
        case TrackingActivity.NotResolved => s"指定された例外 (#${command.index}) はまだ解決されていません"
        case _ => "例外対応の取消しに失敗しました"
      }
      result <- withOptimisticLock("例外対応取消し"):
        repository.clearExceptionResolution(updated, command.index)
    yield result

  /** 例外に補足コメントを追記する (IT8 0.7 / H9): 既存 resolutionNotes に改行区切りで追記する。 */
  def appendResolutionComment(command: AppendResolutionCommentCommand): Either[String, TrackingActivity] =
    for
      tn <- TrackingNumber(command.trackingNumber).left.map(_ => "追跡番号の形式が不正です")
      activity <- repository
        .findByTrackingNumber(tn)
        .toRight(s"追跡番号 ${command.trackingNumber} が見つかりません")
      updated <- activity.appendExceptionComment(command.index, command.comment).left.map {
        case TrackingActivity.ExceptionNotFound => s"指定された例外 (#${command.index}) が見つかりません"
        case TrackingActivity.EmptyComment => "補足コメントは必須です"
        case _ => "補足コメントの追記に失敗しました"
      }
      mergedNotes = updated.exceptions(command.index).resolutionNotes.getOrElse("")
      result <- withOptimisticLock("補足コメント追記"):
        repository.updateExceptionNotes(updated, command.index, mergedNotes)
    yield result

object TrackingCommandService:
  private[commandservices] def eventTypeFor(status: TrackingStatus): Either[String, String] =
    status match
      case TrackingStatus.Received => Right("Receive")
      case TrackingStatus.Loaded => Right("Load")
      case TrackingStatus.Unloaded => Right("Unload")
      case TrackingStatus.Claimed => Right("Claim")
      case TrackingStatus.InException => Right("Customs")
      case other => Left(s"手動更新では指定できない状態です: $other")

/** 状態手動更新コマンド（US17 / IT6）。 */
final case class UpdateTrackingStatusCommand(
    trackingNumber: String,
    status: TrackingStatus,
    locationUnLocode: String,
    occurredAt: Instant
)

/** 追跡番号発行コマンド（US14）。 */
final case class AssignTrackingNumberCommand(bookingId: String)

/** 追跡例外記録コマンド（US19 / US20 / IT7）。 */
final case class RecordExceptionCommand(
    trackingNumber: String,
    exceptionType: cargotracker.tracking.domain.model.enums.ExceptionType,
    locationUnLocode: String,
    occurredAt: Instant,
    description: Option[String] = None
)

/** 追跡例外対応報告コマンド（US19 / US20 / IT7）。 */
final case class ResolveExceptionCommand(
    trackingNumber: String,
    index: Int,
    resolvedAt: Instant,
    resolutionNotes: String
)

/** 追跡例外対応取消しコマンド (IT8 0.7 / H9)。 */
final case class CancelExceptionResolutionCommand(trackingNumber: String, index: Int)

/** 追跡例外への補足コメント追記コマンド (IT8 0.7 / H9)。 */
final case class AppendResolutionCommentCommand(trackingNumber: String, index: Int, comment: String)

/** 追跡イベント記録コマンド（US15）。 */
final case class RecordTrackingEventCommand(
    trackingNumber: String,
    eventType: String,
    eventTime: Instant,
    locationUnLocode: String,
    voyageNumber: Option[String],
    routeDeviation: Boolean
)
