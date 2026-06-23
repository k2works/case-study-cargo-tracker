package cargotracker.billing.infrastructure.acl

import cargotracker.billing.domain.model.repositories.BillingCargoQueryPort
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, BillingCargoSnapshot}
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus}
import cargotracker.shared.domain.ShipperType
import cargotracker.shipper.domain.model.repositories.ShipperRepository

import javax.inject.{Inject, Singleton}

/** Booking + Shipper Context を Billing 用 ACL に変換するアダプター（IT7 0.2 + 0.8 / H2 + H5 解消）。
  *
  * このクラスのみが Billing 側で Booking / Shipper の domain に依存し、ヘキサゴナル境界を一点に集約する。 法人フラグ (`isCorporate`) は Shipper 集約の
  * `shipperType` から自動判定し、UI 入力に依存しない。
  */
@Singleton
class BookingCargoQueryAdapter @Inject() (
    cargoRepository: CargoRepository,
    shipperRepository: ShipperRepository
) extends BillingCargoQueryPort:

  override def findForBilling(bookingId: BillingBookingId): Option[BillingCargoSnapshot] =
    BookingId(bookingId.value).toOption.flatMap(cargoRepository.findById).map { cargo =>
      val isCorporate = shipperRepository
        .findById(cargo.shipperId)
        .exists(_.shipperType == ShipperType.Corporate)
      BillingCargoSnapshot(
        bookingId = BillingBookingId.unsafeFrom(cargo.bookingId.value),
        shipperId = cargo.shipperId.value,
        isCorporate = isCorporate,
        isDelivered = cargo.status == BookingStatus.Delivered,
        origin = cargo.routeSpecification.origin,
        destination = cargo.routeSpecification.destination,
        cargoType = cargo.cargoSpec.cargoType,
        weight = cargo.cargoSpec.weight,
        voyageNumbers = cargo.itinerary.map(_.voyageNumbers).getOrElse(Nil)
      )
    }
