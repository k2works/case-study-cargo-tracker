package cargotracker.shared.domain

/** 貨物重量（kg、最大 9 桁の整数）。 */
opaque type Weight = Long

object Weight:
  sealed trait Error
  case object NonPositive extends Error
  case object TooLarge extends Error

  private val MaxKg = 999_999_999L

  def apply(kg: Long): Either[Error, Weight] =
    if kg <= 0 then Left(NonPositive)
    else if kg > MaxKg then Left(TooLarge)
    else Right(kg)

  /** 永続化からの復元 */
  def unsafeFrom(kg: Long): Weight = kg

  extension (w: Weight) def kg: Long = w
