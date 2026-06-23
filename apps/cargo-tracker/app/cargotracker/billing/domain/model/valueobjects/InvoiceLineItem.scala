package cargotracker.billing.domain.model.valueobjects

import cargotracker.shared.domain.Money

/** 請求書明細行（IT7 0.9 / H6）。
  *
  *   - `category`: 料金内訳の種別 (距離料金 / 重量料金 / 貨物種別料金 など、`LineItemCategory` enum)
  *   - `name`: 表示名 (UI 用、英日混在可)
  *   - `amount`: 当該明細の金額 (JPY)
  */
final case class InvoiceLineItem(
    category: LineItemCategory,
    name: String,
    amount: Money
)

/** 請求書明細行の種別。 */
enum LineItemCategory:
  case Distance
  case Weight
  case CargoType
  case Discount
  case Other

object LineItemCategory:
  def fromName(name: String): Option[LineItemCategory] =
    values.find(_.toString == name)
