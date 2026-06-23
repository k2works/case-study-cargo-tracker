package cargotracker.booking.domain.model.valueobjects

import cargotracker.shared.domain.Location

/** 経路の 1 区間（航海単位）を表す値オブジェクト（IT7 0.14 / O3）。
  *
  *   - `voyageNumber`: 当該区間を担当する航海番号
  *   - `from` / `to`: 当該区間の出発地・到着地（UN/LOCODE）
  *
  * 順序は `Itinerary.legs` の格納順で表現する。
  */
final case class ItineraryLeg(
    voyageNumber: String,
    from: Location,
    to: Location
)
