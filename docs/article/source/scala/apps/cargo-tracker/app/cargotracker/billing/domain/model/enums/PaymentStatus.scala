package cargotracker.billing.domain.model.enums

/** 請求書の支払い状態（US21 → US23、IT8 で NotIssued 追加）。
  *
  *   - `NotIssued`: 請求書は確定したが、支払期日 / 入金参照コードが未発行 (issuePayment 未呼出、IT8 ADR 0019 案 B)
  *   - `Pending`: issuePayment 完了、入金待ち
  *   - `Overdue`: dueDate 超過の Pending
  *   - `Confirmed`: 入金確認済 (confirmPayment 完了)
  *   - `Refunded`: 返金済 (IT9+)
  */
enum PaymentStatus:
  case NotIssued
  case Pending
  case Overdue
  case Confirmed
  case Refunded

object PaymentStatus:
  def fromName(name: String): Option[PaymentStatus] = values.find(_.toString == name)
