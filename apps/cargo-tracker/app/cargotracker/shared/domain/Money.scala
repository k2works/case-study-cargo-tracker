package cargotracker.shared.domain

/** 多通貨対応の金額。
  *
  *   - `amount` は **最小通貨単位**（JPY=円、USD=cent）の整数で保持する
  *   - 等価性は `case class` の構造比較を利用する
  */
final case class Money private (currency: String, amount: Long):

  /** 同一通貨同士で加算する。 */
  def +(other: Money): Either[Money.Error, Money] =
    if currency != other.currency then Left(Money.CurrencyMismatch)
    else Right(new Money(currency, amount + other.amount))

  /** 数量倍する。 */
  def times(factor: Long): Money = copy(amount = amount * factor)

object Money:

  sealed trait Error
  case object NegativeAmount extends Error
  case object CurrencyMismatch extends Error

  /** 任意通貨の Money を生成する。 */
  def apply(currency: String, amount: Long): Either[Error, Money] =
    if amount < 0 then Left(NegativeAmount)
    else Right(new Money(currency, amount))

  /** 日本円の便利コンストラクタ。 */
  def jpy(amount: Long): Either[Error, Money] = apply("JPY", amount)

  val zeroJpy: Money = new Money("JPY", 0L)
