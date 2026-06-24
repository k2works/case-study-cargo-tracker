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

import java.time.{Instant, LocalDate}

/** 請求書（Billing Context 集約ルート）。
  *
  *   - 業務キー: `InvoiceId`（`INV-NNNNNN`）
  *   - `baseAmount` × (1 - `discountRate`) = `finalAmount`（税抜）
  *   - IT7 0.4 / ADR 0015: 金額は `shared.domain.Money` (JPY 単通貨) に統一
  *   - IT7 0.9 / H6: `lineItems` に料金内訳 (距離 / 重量 / 貨物種別 / 割引) を保持
  *   - IT8 ADR 0019 (案 B): Payment は Invoice 集約内のステータスとして表現する。`dueDate` / `paymentReference` は IT8 V23 で追加され、
  *     `issuePayment` / `confirmPayment` / `markOverdue` メソッドで状態遷移する
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
    lineItems: List[InvoiceLineItem],
    dueDate: Option[LocalDate] = None,
    paymentReference: Option[String] = None
):

  /** IT8 US23 (ADR 0019 案 B): 支払期日と入金参照コードを設定し NotIssued → Pending に遷移する。 */
  def issuePayment(dueDate: LocalDate, referenceCode: String): Either[Invoice.Error, Invoice] =
    paymentStatus match
      case PaymentStatus.NotIssued =>
        Right(
          copy(paymentStatus = PaymentStatus.Pending, dueDate = Some(dueDate), paymentReference = Some(referenceCode))
        )
      case _ => Left(Invoice.InvalidPaymentStateTransition(paymentStatus, PaymentStatus.Pending))

  /** IT8 US23: 入金確認。Pending / Overdue → Confirmed に遷移、paidAt を記録する。 */
  def confirmPayment(paidAt: Instant): Either[Invoice.Error, Invoice] =
    paymentStatus match
      case PaymentStatus.Pending | PaymentStatus.Overdue =>
        Right(copy(paymentStatus = PaymentStatus.Confirmed, paidAt = Some(paidAt)))
      case _ => Left(Invoice.InvalidPaymentStateTransition(paymentStatus, PaymentStatus.Confirmed))

  /** IT8 US23: 期日超過判定。Pending + dueDate 設定済 + now > dueDate → Overdue 遷移。 */
  def markOverdue(now: LocalDate): Either[Invoice.Error, Invoice] =
    (paymentStatus, dueDate) match
      case (PaymentStatus.Pending, Some(due)) if now.isAfter(due) =>
        Right(copy(paymentStatus = PaymentStatus.Overdue))
      case _ => Left(Invoice.InvalidPaymentStateTransition(paymentStatus, PaymentStatus.Overdue))

object Invoice:
  sealed trait Error
  case object InvalidAmount extends Error

  /** IT8 ADR 0019: 不正な支払状態遷移 (例: NotIssued → Confirmed 直行)。 */
  final case class InvalidPaymentStateTransition(from: PaymentStatus, to: PaymentStatus) extends Error

  /** Invoice 集約の永続化スナップショット（ADR 0014、IT7 0.9 で lineItems 追加、IT8 0.5 で dueDate / paymentReference 追加）。 */
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
      lineItems: List[InvoiceLineItem] = Nil,
      dueDate: Option[LocalDate] = None,
      paymentReference: Option[String] = None
  )

  /** 請求書を新規発行（US21、IT7 0.9 で lineItems 受領、IT8 ADR 0019 で初期状態を NotIssued に変更）。 `finalAmount` は `baseAmount × (1 -
    * discountRate)` で計算。
    */
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
        paymentStatus = PaymentStatus.NotIssued,
        issuedAt = issuedAt,
        paidAt = None,
        version = 0,
        lineItems = lineItems,
        dueDate = None,
        paymentReference = None
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
      s.lineItems,
      s.dueDate,
      s.paymentReference
    )
