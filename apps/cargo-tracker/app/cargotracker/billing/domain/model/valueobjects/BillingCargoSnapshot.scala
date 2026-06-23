package cargotracker.billing.domain.model.valueobjects

import cargotracker.shared.domain.{CargoType, Location, Weight}

/** Billing Context が請求書発行に必要とする貨物情報のスナップショット（ACL 経由で Booking から取得）。
  *
  * Booking 集約への直接依存を断ち切るため、必要最小限のフィールドだけを露出する。
  */
final case class BillingCargoSnapshot(
    bookingId: BillingBookingId,
    shipperId: String,
    isDelivered: Boolean,
    origin: Location,
    destination: Location,
    cargoType: CargoType,
    weight: Weight,
    voyageNumbers: List[String]
)
