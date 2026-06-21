package cargotracker.booking.domain.model.valueobjects

/** 予約の業務キー識別子。`BK-NNNNNN` 形式（数字 6 桁）。 */
opaque type BookingId = String

object BookingId:
  sealed trait Error
  case object EmptyValue extends Error
  case object InvalidFormat extends Error

  private val Pattern = "^BK-[0-9]{6}$".r

  def apply(raw: String): Either[Error, BookingId] =
    Option(raw) match
      case None | Some("") => Left(EmptyValue)
      case Some(v) =>
        if Pattern.matches(v) then Right(v) else Left(InvalidFormat)

  def unsafeFrom(raw: String): BookingId = raw

  extension (id: BookingId) def value: String = id

/** 予約の状態（domain-model.md 準拠）。 */
enum BookingStatus:
  case Preliminary
  case RouteProposed
  case Confirmed
  case TrackingIssued
  case InTransit
  case Delivered
  case Settled
  case Cancelled

object BookingStatus:
  def fromName(name: String): Option[BookingStatus] =
    values.find(_.toString.equalsIgnoreCase(name))
