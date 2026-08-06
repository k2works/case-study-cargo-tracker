package cargotracker.booking.domain.model.valueobjects

/** Booking Context が保持する追跡番号。Tracking Context の `TrackingNumber` と独立した opaque type で、 横断的な型混同（IT5 セルフレビュー
  * H2）を防ぐ。`TN-NNNNNN` 形式（数字 6 桁）。
  */
opaque type BookingTrackingNumber = String

object BookingTrackingNumber:
  sealed trait Error
  case object EmptyValue extends Error
  case object InvalidFormat extends Error

  private val Pattern = "^TN-[0-9]{6}$".r

  def apply(raw: String): Either[Error, BookingTrackingNumber] =
    Option(raw) match
      case None | Some("") => Left(EmptyValue)
      case Some(v) =>
        if Pattern.matches(v) then Right(v) else Left(InvalidFormat)

  def unsafeFrom(raw: String): BookingTrackingNumber = raw

  extension (tn: BookingTrackingNumber) def value: String = tn
