package cargotracker.booking.infrastructure

import cargotracker.booking.domain.ShipperExistenceChecker
import cargotracker.shared.domain.ShipperId
import cargotracker.shipper.domain.ShipperRepository

import javax.inject.{Inject, Singleton}

/** ShipperRepository を経由した [[ShipperExistenceChecker]] 実装（ACL アダプター）。 */
@Singleton
class ShipperRepositoryBackedExistenceChecker @Inject() (
    shipperRepository: ShipperRepository
) extends ShipperExistenceChecker:

  override def exists(shipperId: ShipperId): Boolean =
    shipperRepository.findById(shipperId).isDefined
