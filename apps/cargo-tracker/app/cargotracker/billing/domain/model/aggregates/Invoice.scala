package cargotracker.billing.domain.model.aggregates

import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, BillingShipperId, DiscountRate, InvoiceId}
import cargotracker.shared.domain.Money

import java.time.Instant

/** 請求書（Billing Context 集約ルート）。
  *
  *   - 業務キー: `InvoiceId`（`INV-NNNNNN`）
  *   - `baseAmount` × (1 - `discountRate`) = `finalAmount`（税抜）
  *   - IT7 0.4 / ADR 0015: 金額は `shared.domain.Money` (JPY 単通貨) に統一
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

  /** Invoice 集約の永続化スナップショット（ADR 0014）。
    *
    * Repository が DB 行から組み立て、ドメイン側で集約に再構成する。 不変条件の検証は `reconstruct` 内で実行される。
    */
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
      version: Int
  )

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
      s.version
    )
