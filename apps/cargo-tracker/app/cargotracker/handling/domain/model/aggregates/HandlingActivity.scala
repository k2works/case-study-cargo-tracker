package cargotracker.handling.domain.model.aggregates

import cargotracker.handling.domain.model.enums.HandlingType
import cargotracker.handling.domain.model.valueobjects.HandlingVoyageNumber
import cargotracker.shared.domain.Location

import java.time.Instant

/** 荷役作業記録（Handling Context 集約ルート）。
  *
  *   - 業務キー: 自動採番 ID（リポジトリで採番）。1 予約に対し時系列で複数件発生する
  *   - `Load` / `Unload` の場合は `voyageNumber` が必須（ドメイン不変条件）
  *   - 採番済の集約は不変（修正は新規追加で表現）
  */
final case class HandlingActivity private (
    bookingId: String,
    eventType: HandlingType,
    completionTime: Instant,
    location: Location,
    voyageNumber: Option[HandlingVoyageNumber],
    operatorName: Option[String],
    routeDeviation: Boolean,
    recipientConfirmation: Option[String],
    version: Int
)

object HandlingActivity:

  sealed trait Error
  case object EmptyBookingId extends Error
  case object VoyageRequired extends Error
  case object RecipientConfirmationRequired extends Error

  /** 荷役作業登録リクエスト（ADR 0014 / 業務操作）。 */
  final case class RegisterRequest(
      bookingId: String,
      eventType: HandlingType,
      completionTime: Instant,
      location: Location,
      voyageNumber: Option[HandlingVoyageNumber],
      operatorName: Option[String],
      routeDeviation: Boolean = false,
      recipientConfirmation: Option[String] = None
  )

  /** 永続化スナップショット（ADR 0014）。 */
  final case class Snapshot(
      bookingId: String,
      eventType: HandlingType,
      completionTime: Instant,
      location: Location,
      voyageNumber: Option[HandlingVoyageNumber],
      operatorName: Option[String],
      routeDeviation: Boolean,
      version: Int,
      recipientConfirmation: Option[String] = None
  )

  def register(req: RegisterRequest): Either[Error, HandlingActivity] =
    if req.bookingId.isEmpty then Left(EmptyBookingId)
    else if req.eventType.requiresVoyage && req.voyageNumber.isEmpty then Left(VoyageRequired)
    else if req.eventType == HandlingType.Claim && req.recipientConfirmation.forall(_.isEmpty) then
      Left(RecipientConfirmationRequired)
    else
      Right(
        new HandlingActivity(
          bookingId = req.bookingId,
          eventType = req.eventType,
          completionTime = req.completionTime,
          location = req.location,
          voyageNumber = req.voyageNumber,
          operatorName = req.operatorName,
          routeDeviation = req.routeDeviation,
          recipientConfirmation = req.recipientConfirmation,
          version = 0
        )
      )

  /** 永続化からの復元（ADR 0014 Snapshot ADT）。 */
  def reconstruct(s: Snapshot): HandlingActivity =
    new HandlingActivity(
      s.bookingId,
      s.eventType,
      s.completionTime,
      s.location,
      s.voyageNumber,
      s.operatorName,
      s.routeDeviation,
      s.recipientConfirmation,
      s.version
    )
