package cargotracker.estimation.domain.model.valueobjects

import cargotracker.shared.domain.Location

import java.time.LocalDate

/** 見積の経路条件（出発地・目的地・希望期限）を束ねる値オブジェクト。
  *
  * Estimation Context 固有。Booking Context の `RouteSpecification` とは独立。
  */
final case class RouteSpec(origin: Location, destination: Location, deadline: LocalDate)
