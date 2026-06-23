package cargotracker.billing.domain.model.valueobjects

/** 割引率（0.0000〜0.3000 の範囲、4 桁小数）。 */
opaque type DiscountRate = BigDecimal

object DiscountRate:
  sealed trait Error
  case object OutOfRange extends Error

  private val Min: BigDecimal = BigDecimal("0.0000")
  private val Max: BigDecimal = BigDecimal("0.3000")

  def apply(value: BigDecimal): Either[Error, DiscountRate] =
    if value == null || value < Min || value > Max then Left(OutOfRange) else Right(value)

  def unsafeFrom(value: BigDecimal): DiscountRate = value
  def zero: DiscountRate = Min

  extension (r: DiscountRate)
    def value: BigDecimal = r
    def percent: BigDecimal = r * 100

/** 割引適用ポリシー種別。 */
enum DiscountPolicyType:
  case CorporateStandard
  case VolumeDiscount
  case Seasonal
  case None
