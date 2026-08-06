package cargotracker.shipper.application.queryservices

import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository

import javax.inject.{Inject, Singleton}

/** 荷主の参照系（CQRS Query 側）。一覧表示・メール重複チェック等を提供する。 */
@Singleton
class ShipperQueryService @Inject() (repository: ShipperRepository):

  def findAll(): Seq[Shipper] = repository.findAll()

  def findByEmail(email: String): Option[Shipper] = repository.findByEmail(email)
