package cargotracker.billing.domain.model.enums

/** 請求書の支払い状態（US21 → US23）。 */
enum PaymentStatus:
  case Pending // 請求書発行直後
  case Confirmed // 支払い確認済 (US23)
  case Overdue // 支払期限超過
  case Refunded // 返金済

object PaymentStatus:
  def fromName(name: String): Option[PaymentStatus] = values.find(_.toString == name)
