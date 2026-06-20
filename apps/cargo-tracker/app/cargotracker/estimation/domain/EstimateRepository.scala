package cargotracker.estimation.domain

trait EstimateRepository:
  def findById(estimateId: EstimateId): Option[Estimate]
  def findAll(): Seq[Estimate]
  def save(estimate: Estimate): Unit
