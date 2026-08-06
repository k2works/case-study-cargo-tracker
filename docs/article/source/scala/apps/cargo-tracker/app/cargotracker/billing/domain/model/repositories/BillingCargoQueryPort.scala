package cargotracker.billing.domain.model.repositories

import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, BillingCargoSnapshot}

/** Billing Context が貨物情報を参照するためのクエリポート（ACL 経由 / IT7 0.2）。
  *
  * Booking 集約への直接依存を排除し、ヘキサゴナル境界を維持する。 インフラ層の `BookingCargoQueryAdapter` が Booking の `CargoRepository` をラップして実装する。
  */
trait BillingCargoQueryPort:
  def findForBilling(bookingId: BillingBookingId): Option[BillingCargoSnapshot]
