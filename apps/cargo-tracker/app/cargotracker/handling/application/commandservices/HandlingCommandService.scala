package cargotracker.handling.application.commandservices

import cargotracker.handling.domain.model.aggregates.HandlingActivity
import cargotracker.handling.domain.model.enums.HandlingType
import cargotracker.handling.domain.model.repositories.HandlingActivityRepository
import cargotracker.handling.domain.model.valueobjects.HandlingVoyageNumber
import cargotracker.shared.domain.Location

import java.time.Instant
import javax.inject.{Inject, Singleton}

/** 荷役登録コマンドサービス（US15）。
  *
  *   - 入力検証 + `HandlingActivity` 集約の生成・永続化のみを担う
  *   - Booking / Tracking との連携は Controller 層が順次オーケストレーションする（ArchUnit ルール 3 準拠）
  */
@Singleton
class HandlingCommandService @Inject() (repository: HandlingActivityRepository):

  def register(command: RegisterHandlingActivityCommand): Either[String, HandlingActivity] =
    for
      eventType <- HandlingType
        .fromName(command.eventType)
        .toRight(s"荷役種別が不正です: ${command.eventType}")
      location <- Location(command.locationUnLocode).left
        .map(_ => "作業場所の UnLocode が不正です")
      voyageNumber = command.voyageNumber
        .filter(_.nonEmpty)
        .flatMap(HandlingVoyageNumber(_).toOption)
      activity <- HandlingActivity
        .register(
          bookingId = command.bookingId,
          eventType = eventType,
          completionTime = command.completionTime,
          location = location,
          voyageNumber = voyageNumber,
          operatorName = command.operatorName.filter(_.nonEmpty),
          routeDeviation = command.routeDeviation
        )
        .left
        .map {
          case HandlingActivity.VoyageRequired => s"$eventType は航海番号が必須です"
          case HandlingActivity.EmptyBookingId => "予約 ID が空です"
          case HandlingActivity.RecipientConfirmationRequired => "引取作業 (Claim) には荷受人確認が必須です"
        }
    yield
      repository.save(activity)
      activity

/** 荷役登録コマンド（US15）。 */
final case class RegisterHandlingActivityCommand(
    bookingId: String,
    eventType: String,
    completionTime: Instant,
    locationUnLocode: String,
    voyageNumber: Option[String],
    operatorName: Option[String],
    routeDeviation: Boolean = false
)
