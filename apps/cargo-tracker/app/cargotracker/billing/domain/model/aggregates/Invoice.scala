package cargotracker.billing.domain.model.aggregates

import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.valueobjects.{
  BillingBookingId,
  BillingShipperId,
  DiscountRate,
  InvoiceId,
  Money
}

import java.time.Instant

/** 請求書（Billing Context 集約ルート）。
  *
  *   - 業務キー: `InvoiceId`（`INV-NNNNNN`）
  *   - `baseAmount` × (1 - `discountRate`) = `finalAmount`（税抜）
  *   - IT6 US21 は `PaymentStatus.Pending` で発行。`Confirmed` 遷移は IT8 US23 で実装
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
    version: Int
)

object Invoice:
  sealed trait Error
  case object InvalidAmount extends Error

  /** 請求書を新規発行（US21）。`finalAmount` は `baseAmount × (1 - discountRate)` で計算。 */
  def issue(
      invoiceId: InvoiceId,
      cargoBookingId: BillingBookingId,
      shipperId: BillingShipperId,
      baseAmount: Money,
      discountRate: DiscountRate,
      issuedAt: Instant
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
        version = 0
      )
    )

  /** 永続化からの復元。 */
  def reconstruct(
      invoiceId: InvoiceId,
      cargoBookingId: BillingBookingId,
      shipperId: BillingShipperId,
      baseAmount: Money,
      discountRate: DiscountRate,
      finalAmount: Money,
      paymentStatus: PaymentStatus,
      issuedAt: Instant,
      paidAt: Option[Instant],
      version: Int
  ): Invoice =
    new Invoice(
      invoiceId,
      cargoBookingId,
      shipperId,
      baseAmount,
      discountRate,
      finalAmount,
      paymentStatus,
      issuedAt,
      paidAt,
      version
    )
