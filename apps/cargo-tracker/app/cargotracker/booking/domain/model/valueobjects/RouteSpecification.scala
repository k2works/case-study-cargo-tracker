package cargotracker.booking.domain.model.valueobjects

import cargotracker.shared.domain.Location

import java.time.LocalDate

/** 輸送経路の仕様。出発地・目的地・到着期限を保持する。 */
final case class RouteSpecification(
    origin: Location,
    destination: Location,
    arrivalDeadline: LocalDate
)

object RouteSpecification:
  sealed trait Error
  case object SameOriginAndDestination extends Error

  def apply(
      origin: Location,
      destination: Location,
      arrivalDeadline: LocalDate
  ): Either[Error, RouteSpecification] =
    if origin.unLocode == destination.unLocode then Left(SameOriginAndDestination)
    else Right(new RouteSpecification(origin, destination, arrivalDeadline))
