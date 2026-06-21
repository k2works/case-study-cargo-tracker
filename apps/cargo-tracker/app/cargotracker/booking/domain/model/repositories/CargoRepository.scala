package cargotracker.booking.domain.model.repositories

import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus}

trait CargoRepository:
  def findById(bookingId: BookingId): Option[Cargo]
  def findAll(): Seq[Cargo]

  /** 指定状態の予約のみ取得する（US06 経路設計者ダッシュボード）。 */
  def findByStatus(status: BookingStatus): Seq[Cargo]

  def save(cargo: Cargo): Unit
  def nextIdentity(): BookingId
