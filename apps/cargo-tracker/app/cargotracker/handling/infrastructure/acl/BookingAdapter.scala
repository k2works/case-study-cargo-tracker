package cargotracker.handling.infrastructure.acl

import cargotracker.booking.application.commandservices.BookingCommandService
import cargotracker.handling.domain.model.ports.BookingNotificationPort

import javax.inject.{Inject, Singleton}

/** Booking Context を Handling 用に橋渡しする ACL アダプター（IT7 0.3）。 */
@Singleton
class BookingAdapter @Inject() (bookingCommandService: BookingCommandService) extends BookingNotificationPort:

  override def logHandling(
      bookingId: String,
      trackingNumber: String,
      eventType: String,
      locationUnLocode: String
  ): Either[String, Unit] =
    bookingCommandService.logHandlingNotification(bookingId, trackingNumber, eventType, locationUnLocode)

  override def completeDelivery(
      bookingId: String,
      trackingNumber: String,
      locationUnLocode: String,
      recipientConfirmation: String
  ): Either[String, Unit] =
    bookingCommandService
      .completeDelivery(bookingId, trackingNumber, locationUnLocode, recipientConfirmation)
      .map(_ => ())
