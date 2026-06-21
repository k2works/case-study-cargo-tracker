package cargotracker.booking.domain.model.valueobjects

/** 温度管理条件（US05 冷凍・冷蔵貨物）。
  *
  *   - 最低温度 ≤ 最高温度
  *   - 単位は摂氏 / 華氏のいずれか
  *   - 温度値は整数（小数管理が必要になった時点で `BigDecimal` に拡張）
  */
final case class RefrigerationSpec private (
    minTemperature: Int,
    maxTemperature: Int,
    unit: TemperatureUnit
)

object RefrigerationSpec:

  sealed trait Error
  case object InvalidTemperatureRange extends Error

  def apply(
      minTemperature: Int,
      maxTemperature: Int,
      unit: TemperatureUnit
  ): Either[Error, RefrigerationSpec] =
    if minTemperature > maxTemperature then Left(InvalidTemperatureRange)
    else Right(new RefrigerationSpec(minTemperature, maxTemperature, unit))

/** 温度単位。 */
enum TemperatureUnit:
  case Celsius
  case Fahrenheit

object TemperatureUnit:

  def fromName(name: String): Option[TemperatureUnit] =
    values.find(_.toString.equalsIgnoreCase(name))
