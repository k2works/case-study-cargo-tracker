package cargotracker.billing.domain.model.aggregates

import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.valueobjects.{
  BillingBookingId,
  BillingShipperId,
  DiscountRate,
  InvoiceId,
  InvoiceLineItem
}
import cargotracker.shared.domain.Money

import java.time.Instant

/** 請求書（Billing Context 集約ルート）。
  *
  *   - 業務キー: `InvoiceId`（`INV-NNNNNN`）
  *   - `baseAmount` × (1 - `discountRate`) = `finalAmount`（税抜）
  *   - IT7 0.4 / ADR 0015: 金額は `shared.domain.Money` (JPY 単通貨) に統一
  *   - IT7 0.9 / H6: `lineItems` に料金内訳 (距離 / 重量 / 貨物種別 / 割引) を保持
  */
final case class Invoice private (
    invoiceId: InvoiceId,
    cargoBookingId: BillingBookingId,
    shipperId: BillingShipperId,
    baseAmount: Money,
    discountRate: DiscountRate,
    finalAmount: Money,
    paymentStatus: PaymentStatus,
    issuedAt: Instant,
    paidAt: Option[Instant],
    version: Int,
    lineItems: List[InvoiceLineItem]
)

object Invoice:
  sealed trait Error
  case object InvalidAmount extends Error

  /** Invoice 集約の永続化スナップショット（ADR 0014、IT7 0.9 で lineItems 追加）。 */
  final case class Snapshot(
      invoiceId: InvoiceId,
      cargoBookingId: BillingBookingId,
      shipperId: BillingShipperId,
      baseAmount: Money,
      discountRate: DiscountRate,
      finalAmount: Money,
      paymentStatus: PaymentStatus,
      issuedAt: Instant,
      paidAt: Option[Instant],
      version: Int,
      lineItems: List[InvoiceLineItem] = Nil
  )

  /** 請求書を新規発行（US21、IT7 0.9 で lineItems 受領）。`finalAmount` は `baseAmount × (1 - discountRate)` で計算。 */
  def issue(
      invoiceId: InvoiceId,
      cargoBookingId: BillingBookingId,
      shipperId: BillingShipperId,
      baseAmount: Money,
      discountRate: DiscountRate,
      issuedAt: Instant,
      lineItems: List[InvoiceLineItem] = Nil
  ): Either[Error, Invoice] =
    val finalAmount: Money = baseAmount.multiplyByRate(BigDecimal(1) - discountRate.value)
    Right(
      new Invoice(
        invoiceId = invoiceId,
        cargoBookingId = cargoBookingId,
        shipperId = shipperId,
        baseAmount = baseAmount,
        discountRate = discountRate,
        finalAmount = finalAmount,
        paymentStatus = PaymentStatus.Pending,
        issuedAt = issuedAt,
        paidAt = None,
        version = 0,
        lineItems = lineItems
      )
    )

  /** 永続化からの復元（ADR 0014 Snapshot ADT）。 */
  def reconstruct(s: Snapshot): Invoice =
    new Invoice(
      s.invoiceId,
      s.cargoBookingId,
      s.shipperId,
      s.baseAmount,
      s.discountRate,
      s.finalAmount,
      s.paymentStatus,
      s.issuedAt,
      s.paidAt,
      s.version,
      s.lineItems
    )
