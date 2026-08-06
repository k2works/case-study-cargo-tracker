package cargotracker.estimation.domain.model.valueobjects

import cargotracker.shared.domain.{CargoType, Weight}

/** 見積の貨物条件（種別・重量）を束ねる値オブジェクト。
  *
  * Estimation Context 固有。Booking Context の `CargoSpec`（危険物・冷凍属性を含む詳細版）とは独立。
  */
final case class CargoSpec(cargoType: CargoType, weight: Weight)
