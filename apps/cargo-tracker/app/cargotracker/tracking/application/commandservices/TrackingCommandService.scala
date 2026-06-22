package cargotracker.tracking.application.commandservices

import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingNumber}

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

/** 追跡番号発行コマンド（US14）。 */
final case class AssignTrackingNumberCommand(bookingId: String)
