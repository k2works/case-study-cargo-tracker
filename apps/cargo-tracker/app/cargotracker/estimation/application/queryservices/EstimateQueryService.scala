package cargotracker.estimation.application.queryservices

import cargotracker.estimation.domain.model.aggregates.Estimate
import cargotracker.estimation.domain.model.repositories.EstimateRepository
import cargotracker.estimation.domain.model.valueobjects.EstimateId

import javax.inject.{Inject, Singleton}

/** 輸送見積の参照系（CQRS Query 側）。 */
@Singleton
class EstimateQueryService @Inject() (repository: EstimateRepository):

  def findAll(): Seq[Estimate] = repository.findAll()

  def findById(estimateId: String): Option[Estimate] =
    repository.findById(EstimateId.unsafeFrom(estimateId))
