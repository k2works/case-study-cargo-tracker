package cargotracker.billing.infrastructure.acl

import cargotracker.billing.domain.model.repositories.BillingCargoQueryPort
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, BillingCargoSnapshot}
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus}

import javax.inject.{Inject, Singleton}

/** Booking Context の `CargoRepository` を Billing 用 ACL に変換するアダプター（IT7 0.2 / ADR 0014 関連）。
  *
  * このクラスのみが Billing 側で Booking の domain に依存し、ヘキサゴナル境界を一点に集約する。
  */
@Singleton
class BookingCargoQueryAdapter @Inject() (cargoRepository: CargoRepository) extends BillingCargoQueryPort:

  override def findForBilling(bookingId: BillingBookingId): Option[BillingCargoSnapshot] =
    BookingId(bookingId.value).toOption.flatMap(cargoRepository.findById).map { cargo =>
      BillingCargoSnapshot(
        bookingId = BillingBookingId.unsafeFrom(cargo.bookingId.value),
        shipperId = cargo.shipperId.value,
        isDelivered = cargo.status == BookingStatus.Delivered,
        origin = cargo.routeSpecification.origin,
        destination = cargo.routeSpecification.destination,
        cargoType = cargo.cargoSpec.cargoType,
        weight = cargo.cargoSpec.weight,
        voyageNumbers = cargo.itinerary.map(_.voyageNumbers).getOrElse(Nil)
      )
    }
