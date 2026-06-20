package cargotracker.booking.domain

trait CargoRepository:
  def findById(bookingId: BookingId): Option[Cargo]
  def findAll(): Seq[Cargo]
  def save(cargo: Cargo): Unit
  def nextIdentity(): BookingId
