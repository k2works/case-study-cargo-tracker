package cargotracker.handling.infrastructure.acl

import cargotracker.booking.application.api.BookingPublicApi
import cargotracker.handling.domain.model.ports.BookingNotificationPort

import javax.inject.{Inject, Singleton}

/** Booking Context を Handling 用に橋渡しする ACL アダプター（IT7 0.3 / IT8 0.3 ADR 0017 / H3 解消）。
  *
  * IT8 で `BookingCommandService` 直接依存から `BookingPublicApi` trait 依存へ切り替え、Booking 内部実装変更が Handling に 波及しない構造を確立した。
  */
@Singleton
class BookingAdapter @Inject() (bookingPublicApi: BookingPublicApi) extends BookingNotificationPort:

  override def logHandling(
      bookingId: String,
      trackingNumber: String,
      eventType: String,
      locationUnLocode: String
  ): Either[String, Unit] =
    bookingPublicApi.logHandlingNotification(bookingId, trackingNumber, eventType, locationUnLocode)

  override def completeDelivery(
      bookingId: String,
      trackingNumber: String,
      locationUnLocode: String,
      recipientConfirmation: String
  ): Either[String, Unit] =
    bookingPublicApi
      .completeDelivery(bookingId, trackingNumber, locationUnLocode, recipientConfirmation)
      .map(_ => ())
