package cargotracker.routing.application.queryservices

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.VoyageNumber

import javax.inject.{Inject, Singleton}

/** 航海の参照系（CQRS Query 側、IT3 US07 検索の前段）。 */
@Singleton
class VoyageQueryService @Inject() (repository: VoyageRepository):

  def findAll(): Seq[Voyage] = repository.findAll()

  def findByVoyageNumber(voyageNumber: String): Option[Voyage] =
    VoyageNumber(voyageNumber).toOption.flatMap(repository.findByVoyageNumber)
