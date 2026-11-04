package cargotracker.booking.domain

import cargotracker.sharedkernel.Ulid

/** 荷主の ID（ULID）。 */
final case class ShipperId(value: Ulid)

/** 貨物予約の集約 ID（ULID）。人が読む予約番号（`BookingNumber`）とは別物（ADR-003）。 */
final case class BookingId(value: Ulid)
