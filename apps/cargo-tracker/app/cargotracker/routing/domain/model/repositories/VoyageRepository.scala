package cargotracker.routing.domain.model.repositories

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.valueobjects.VoyageNumber

/** 航海集約の永続化ポート。 */
trait VoyageRepository:
  def findByVoyageNumber(voyageNumber: VoyageNumber): Option[Voyage]
  def findAll(): Seq[Voyage]
  def save(voyage: Voyage): Unit
