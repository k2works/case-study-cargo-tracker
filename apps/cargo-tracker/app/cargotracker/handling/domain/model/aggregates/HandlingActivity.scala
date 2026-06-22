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
    version: Int
)

object HandlingActivity:

  sealed trait Error
  case object EmptyBookingId extends Error
  case object VoyageRequired extends Error

  def register(
      bookingId: String,
      eventType: HandlingType,
      completionTime: Instant,
      location: Location,
      voyageNumber: Option[HandlingVoyageNumber],
      operatorName: Option[String],
      routeDeviation: Boolean = false
  ): Either[Error, HandlingActivity] =
    if bookingId.isEmpty then Left(EmptyBookingId)
    else if eventType.requiresVoyage && voyageNumber.isEmpty then Left(VoyageRequired)
    else
      Right(
        new HandlingActivity(
          bookingId = bookingId,
          eventType = eventType,
          completionTime = completionTime,
          location = location,
          voyageNumber = voyageNumber,
          operatorName = operatorName,
          routeDeviation = routeDeviation,
          version = 0
        )
      )

  /** 永続化からの復元。 */
  def reconstruct(
      bookingId: String,
      eventType: HandlingType,
      completionTime: Instant,
      location: Location,
      voyageNumber: Option[HandlingVoyageNumber],
      operatorName: Option[String],
      routeDeviation: Boolean,
      version: Int
  ): HandlingActivity =
    new HandlingActivity(
      bookingId,
      eventType,
      completionTime,
      location,
      voyageNumber,
      operatorName,
      routeDeviation,
      version
    )
