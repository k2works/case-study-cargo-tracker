package cargotracker.booking.domain.model.repositories

import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.valueobjects.BookingId
trait CargoRepository:
  def findById(bookingId: BookingId): Option[Cargo]
  def findAll(): Seq[Cargo]
  def save(cargo: Cargo): Unit
  def nextIdentity(): BookingId
