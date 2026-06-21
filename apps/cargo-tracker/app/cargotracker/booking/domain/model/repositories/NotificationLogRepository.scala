package cargotracker.booking.domain.model.repositories

import cargotracker.booking.domain.model.aggregates.NotificationLog
import cargotracker.booking.domain.model.valueobjects.BookingId

/** 通知ログの永続化ポート（US12 / US13）。 */
trait NotificationLogRepository:
  def findByBookingId(bookingId: BookingId): Seq[NotificationLog]
  def save(log: NotificationLog): Unit
