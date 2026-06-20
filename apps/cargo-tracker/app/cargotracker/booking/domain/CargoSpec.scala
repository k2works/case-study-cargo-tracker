package cargotracker.booking.domain

import cargotracker.shared.domain.{CargoType, Weight}

/** 貨物仕様。種別・重量・品名等を保持する。 */
final case class CargoSpec(
    cargoType: CargoType,
    weight: Weight,
    description: Option[String],
    quantity: Option[Int],
    hazardous: Option[HazardousDeclaration]
)

/** 危険物申告情報。 */
final case class HazardousDeclaration(
    hazardClass: String,
    unNumber: String,
    properShippingName: String
)
