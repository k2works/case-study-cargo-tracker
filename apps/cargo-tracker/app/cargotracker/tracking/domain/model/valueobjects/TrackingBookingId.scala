package cargotracker.tracking.domain.model.valueobjects

/** Tracking Context 内で扱う予約 ID。Booking Context の `BookingId` とは別型（コンテキスト分離 / ADR 0010）。 */
opaque type TrackingBookingId = String

object TrackingBookingId:
  sealed trait Error
  case object Empty extends Error

  def apply(raw: String): Either[Error, TrackingBookingId] =
    if raw.nonEmpty then Right(raw) else Left(Empty)

  def unsafeFrom(raw: String): TrackingBookingId =
    apply(raw).fold(_ => throw IllegalArgumentException(s"Invalid TrackingBookingId: $raw"), identity)

  extension (id: TrackingBookingId) def value: String = id
