package cargotracker.routing.domain.model.valueobjects

import cargotracker.shared.domain.Location

import java.time.Instant

/** 探索結果の経路（辺の列）。Routing コンテキスト固有の値オブジェクト（ADR 0006）。
  *
  * 不変条件: 1 区間以上を保持する。
  */
final case class RouteCandidate(legs: List[RoutingLeg]):
  require(legs.nonEmpty, "経路は 1 区間以上必要")
  def origin: Location = legs.head.from
  def destination: Location = legs.last.to
  def departure: Instant = legs.head.departure
  def arrival: Instant = legs.last.arrival
  def transitDays: Long =
    java.time.Duration.between(departure, arrival).toDays
  def voyages: List[VoyageNumber] = legs.map(_.voyageNumber).distinct
