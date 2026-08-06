package cargotracker.routing.domain.model.valueobjects

import cargotracker.shared.domain.Location

import java.time.Instant

/** 経路探索の入力辺（ADR 0006）。1 航海の 1 区間に相当する Routing コンテキスト固有の値オブジェクト。
  *
  * Estimation コンテキストの `RouteCandidate`（PricingService 用）と区別する。
  */
final case class RoutingLeg(
    voyageNumber: VoyageNumber,
    from: Location,
    to: Location,
    departure: Instant,
    arrival: Instant
)
