package cargotracker.billing.domain.model.valueobjects

/** 金額（最小単位は円、Long で保持）。負数は不可。 */
opaque type Money = Long

object Money:
  sealed trait Error
  case object Negative extends Error

  def apply(value: Long): Either[Error, Money] =
    if value < 0L then Left(Negative) else Right(value)

  def unsafeFrom(value: Long): Money = value
  def zero: Money = 0L

  extension (m: Money)
    def value: Long = m
    def plus(other: Money): Money = m + other
    def minus(other: Money): Money = if m - other < 0 then 0L else m - other
    def multiplyByRate(rate: BigDecimal): Money =
      (BigDecimal(m) * rate).setScale(0, BigDecimal.RoundingMode.HALF_UP).toLong
