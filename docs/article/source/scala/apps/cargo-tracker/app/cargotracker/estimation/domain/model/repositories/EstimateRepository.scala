package cargotracker.estimation.domain.model.repositories

import cargotracker.estimation.domain.model.aggregates.Estimate
import cargotracker.estimation.domain.model.valueobjects.EstimateId
trait EstimateRepository:
  def findById(estimateId: EstimateId): Option[Estimate]
  def findAll(): Seq[Estimate]
  def save(estimate: Estimate): Unit
