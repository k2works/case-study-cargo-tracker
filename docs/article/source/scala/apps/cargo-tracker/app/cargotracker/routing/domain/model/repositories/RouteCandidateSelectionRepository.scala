package cargotracker.routing.domain.model.repositories

import cargotracker.routing.domain.model.aggregates.RouteCandidateSelection

/** 経路選択集約の永続化ポート（US09）。 */
trait RouteCandidateSelectionRepository:
  def findByBookingId(bookingId: String): Option[RouteCandidateSelection]
  def save(selection: RouteCandidateSelection): Unit
