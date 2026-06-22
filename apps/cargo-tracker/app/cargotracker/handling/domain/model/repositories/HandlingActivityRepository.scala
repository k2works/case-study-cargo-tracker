package cargotracker.handling.domain.model.repositories

import cargotracker.handling.domain.model.aggregates.HandlingActivity

trait HandlingActivityRepository:
  def save(activity: HandlingActivity): Unit
  def findByBookingId(bookingId: String): Seq[HandlingActivity]
  def findAll(): Seq[HandlingActivity]
