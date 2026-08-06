package cargotracker.billing.domain.model.valueobjects

/** 請求書識別子。`INV-NNNNNN` 形式（数字 6 桁）。 */
opaque type InvoiceId = String

object InvoiceId:
  sealed trait Error
  case object EmptyValue extends Error
  case object InvalidFormat extends Error

  private val Pattern = "^INV-[0-9]{6}$".r

  def apply(raw: String): Either[Error, InvoiceId] =
    Option(raw) match
      case None | Some("") => Left(EmptyValue)
      case Some(v) => if Pattern.matches(v) then Right(v) else Left(InvalidFormat)

  def unsafeFrom(raw: String): InvoiceId = raw
  def fromSequence(n: Long): InvoiceId = f"INV-$n%06d"

  extension (id: InvoiceId) def value: String = id

/** 請求対象の予約 ID（Booking Context との独立性確保のため Billing 用 opaque type）。 */
opaque type BillingBookingId = String

object BillingBookingId:
  def apply(raw: String): Either[String, BillingBookingId] =
    Option(raw).filter(_.nonEmpty).toRight("予約 ID が空です")
  def unsafeFrom(raw: String): BillingBookingId = raw
  extension (id: BillingBookingId) def value: String = id

/** 請求対象の荷主 ID + 法人フラグ。 */
final case class BillingShipperId(value: String, isCorporate: Boolean)
