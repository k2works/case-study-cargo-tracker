package cargotracker.booking.domain.model.valueobjects

import cargotracker.shared.domain.{CargoType, Weight}

/** 貨物仕様。種別・重量・品名等を保持する（US04 + US05）。
  *
  * 不変条件:
  *   - `cargoType == Hazardous` の場合 `hazardous.isDefined`
  *   - `cargoType == Refrigerated` の場合 `refrigeration.isDefined`
  *
  * スマートコンストラクタ [[CargoSpec.create]] でこれらを検証する。 既存呼び出しの後方互換のためバリデーション不要の `apply` も残す。
  */
final case class CargoSpec(
    cargoType: CargoType,
    weight: Weight,
    description: Option[String],
    quantity: Option[Int],
    hazardous: Option[HazardousDeclaration],
    refrigeration: Option[RefrigerationSpec] = None
)

object CargoSpec:

  sealed trait Error
  case object HazardousDeclarationRequired extends Error
  case object RefrigerationSpecRequired extends Error

  /** 条件付き必須を検証して生成する（US05 受入条件）。 */
  def create(
      cargoType: CargoType,
      weight: Weight,
      description: Option[String],
      quantity: Option[Int],
      hazardous: Option[HazardousDeclaration],
      refrigeration: Option[RefrigerationSpec]
  ): Either[Error, CargoSpec] =
    (cargoType, hazardous, refrigeration) match
      case (CargoType.Hazardous, None, _) => Left(HazardousDeclarationRequired)
      case (CargoType.Refrigerated, _, None) => Left(RefrigerationSpecRequired)
      case _ =>
        Right(CargoSpec(cargoType, weight, description, quantity, hazardous, refrigeration))

/** 危険物申告情報。 */
final case class HazardousDeclaration(
    hazardClass: String,
    unNumber: String,
    properShippingName: String
)
