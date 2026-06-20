package cargotracker.shipper.domain

/** 法人荷主の割引率（0.0〜0.30）。 */
opaque type DiscountRate = Double

object DiscountRate:

  sealed trait Error
  case object OutOfRange extends Error

  private val Min = 0.0
  private val Max = 0.30

  def apply(value: Double): Either[Error, DiscountRate] =
    if value < Min || value > Max then Left(OutOfRange) else Right(value)

  /** 割引率 0%（個人荷主用のデフォルト） */
  val zero: DiscountRate = 0.0

  extension (r: DiscountRate) def value: Double = r
