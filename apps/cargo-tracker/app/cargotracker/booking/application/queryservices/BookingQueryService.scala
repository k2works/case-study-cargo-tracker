package cargotracker.booking.application.queryservices

import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus}

import javax.inject.{Inject, Singleton}

/** 貨物予約の参照系（CQRS Query 側）。 */
@Singleton
class BookingQueryService @Inject() (repository: CargoRepository):

  def findAll(): Seq[Cargo] = repository.findAll()

  def findById(bookingId: String): Option[Cargo] =
    repository.findById(BookingId.unsafeFrom(bookingId))

  /** 経路設計者ダッシュボード用: 引き渡し済み（RouteProposed）の予約一覧。 */
  def findRouteProposed(): Seq[Cargo] = repository.findByStatus(BookingStatus.RouteProposed)
