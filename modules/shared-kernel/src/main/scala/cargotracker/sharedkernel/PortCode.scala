package cargotracker.sharedkernel

/** 港湾コード（UN/LOCODE：国 2 文字 + 場所 3 文字。場所は英字と数字 2〜9）。
  * ここでは**形式だけ**を検証する。実在するコードかどうかは参照データ（reference.port）で確かめる（R-09）。
  */
final case class PortCode private (value: String):
  def country: String = value.take(2)
  def location: String = value.drop(2)
  override def toString: String = value

object PortCode:
  private val Pattern = "^[A-Z]{2}[A-Z2-9]{3}$".r

  /** `field` は、エラーに載せる項目名（呼び出し側が、出発地か目的地かを決める）。 */
  def parse(raw: String, field: String = "portCode"): Either[InvalidValue, PortCode] =
    val v = raw.trim.toUpperCase
    Either.cond(
      Pattern.matches(v),
      PortCode(v),
      InvalidValue(field, "UN/LOCODE（国 2 文字 + 場所 3 文字）ではない")
    )
