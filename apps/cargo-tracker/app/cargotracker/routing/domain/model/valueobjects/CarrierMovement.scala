package cargotracker.routing.domain.model.valueobjects

import cargotracker.shared.domain.Location

import java.time.Instant

/** 運送区間（domain-model.md 準拠）。
  *
  * 不変条件:
  *   - 出発地 != 到着地
  *   - 出発時刻 < 到着時刻（US24 日付整合性検証）
  */
final case class CarrierMovement private (
    departureLocation: Location,
    arrivalLocation: Location,
    departureTime: Instant,
    arrivalTime: Instant
)

object CarrierMovement:

  sealed trait Error
  case object SameOriginAndDestination extends Error
  case object InvalidTimeRange extends Error

  def apply(
      departureLocation: Location,
      arrivalLocation: Location,
      departureTime: Instant,
      arrivalTime: Instant
  ): Either[Error, CarrierMovement] =
    if departureLocation == arrivalLocation then Left(SameOriginAndDestination)
    else if !departureTime.isBefore(arrivalTime) then Left(InvalidTimeRange)
    else
      Right(
        new CarrierMovement(
          departureLocation,
          arrivalLocation,
          departureTime,
          arrivalTime
        )
      )
