package cargotracker.booking.infrastructure.services

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.shared.domain.ShipperId
import cargotracker.shipper.domain.model.repositories.ShipperRepository

import javax.inject.{Inject, Singleton}

/** ShipperRepository を経由した [[ShipperExistenceChecker]] 実装（ACL アダプター）。 */
@Singleton
class ShipperRepositoryBackedExistenceChecker @Inject() (
    shipperRepository: ShipperRepository
) extends ShipperExistenceChecker:

  override def exists(shipperId: ShipperId): Boolean =
    shipperRepository.findById(shipperId).isDefined
