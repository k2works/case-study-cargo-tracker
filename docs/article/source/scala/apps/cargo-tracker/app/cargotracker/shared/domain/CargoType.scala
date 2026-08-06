package cargotracker.shared.domain

/** 貨物種別。料金計算の係数決定に利用する。 */
enum CargoType:
  case General
  case Hazardous
  case Refrigerated

object CargoType:
  def fromName(name: String): Option[CargoType] =
    values.find(_.toString.equalsIgnoreCase(name))
