package cargotracker.handling.infrastructure.acl

import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.BookingId
import cargotracker.handling.domain.model.ports.HandlingCargoQueryPort

import javax.inject.{Inject, Singleton}

/** Booking Context を Handling 用に橋渡しする ACL アダプター (IT8 0.11 / T3 解消)。
  *
  * このクラスのみが Handling 側で Booking の domain に依存する。`HandlingOrchestrator` から `HandlingCargoQueryPort` 経由で 呼び出され、荷役発生地と貨物
  * itinerary の照合を行う。
  */
@Singleton
class BookingCargoForHandlingAdapter @Inject() (cargoRepository: CargoRepository) extends HandlingCargoQueryPort:

  override def isOnRoute(bookingId: String, unLocode: String): Boolean =
    findCargoItineraryLocations(bookingId).exists(_.contains(unLocode))

  override def findItineraryLocations(bookingId: String): Option[Set[String]] =
    findCargoItineraryLocations(bookingId)

  private def findCargoItineraryLocations(bookingId: String): Option[Set[String]] =
    BookingId(bookingId).toOption
      .flatMap(cargoRepository.findById)
      .flatMap(_.itinerary)
      .map(_.expectedLocations)
