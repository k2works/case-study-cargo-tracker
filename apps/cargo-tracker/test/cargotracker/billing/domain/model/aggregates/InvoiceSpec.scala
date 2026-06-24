package cargotracker.billing.domain.model.aggregates

import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, BillingShipperId, DiscountRate, InvoiceId}
import cargotracker.shared.domain.Money
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Instant, LocalDate}

class InvoiceSpec extends AnyFunSuite with Matchers with EitherValues:

  private val id = InvoiceId.fromSequence(1)
  private val bid = BillingBookingId.unsafeFrom("BK-000001")
  private val sid = BillingShipperId("SH-000001", isCorporate = false)
  private val now = Instant.parse("2026-09-15T10:00:00Z")

  test("issue: 割引率 0 のとき finalAmount = baseAmount"):
    val Right(inv) =
      Invoice.issue(id, bid, sid, Money.unsafeFromJpy(10000L), DiscountRate.zero, now): @unchecked
    inv.baseAmount.amount shouldBe 10000L
    inv.finalAmount.amount shouldBe 10000L
    inv.baseAmount.currency shouldBe "JPY"
    // IT8 ADR 0019 (案 B): issue 直後は NotIssued、issuePayment で Pending 遷移
    inv.paymentStatus shouldBe PaymentStatus.NotIssued
    inv.dueDate shouldBe None
    inv.paymentReference shouldBe None

  test("issue: 割引率 10% で finalAmount = baseAmount × 0.9"):
    val Right(dr) = DiscountRate(BigDecimal("0.1000")): @unchecked
    val Right(inv) =
      Invoice.issue(id, bid, sid, Money.unsafeFromJpy(10000L), dr, now): @unchecked
    inv.finalAmount.amount shouldBe 9000L

  test("DiscountRate: 範囲外 (-0.01 / 0.31) は OutOfRange"):
    DiscountRate(BigDecimal("-0.01")) shouldBe Left(DiscountRate.OutOfRange)
    DiscountRate(BigDecimal("0.31")) shouldBe Left(DiscountRate.OutOfRange)

  test("Money: 負数は NegativeAmount エラー (shared.domain.Money 統一後)"):
    Money.jpy(-1L) shouldBe Left(Money.NegativeAmount)

  // IT8 US23 ADR 0019 (案 B): 支払状態遷移 6 件

  private def freshInvoice: Invoice =
    Invoice.issue(id, bid, sid, Money.unsafeFromJpy(10000L), DiscountRate.zero, now).value

  test("issuePayment: NotIssued → Pending 遷移 + dueDate/paymentReference 設定 (IT8 US23)"):
    val due = LocalDate.parse("2026-10-31")
    val updated = freshInvoice.issuePayment(due, "PAY-REF-001").value
    updated.paymentStatus shouldBe PaymentStatus.Pending
    updated.dueDate shouldBe Some(due)
    updated.paymentReference shouldBe Some("PAY-REF-001")

  test("issuePayment: NotIssued 以外 (Pending / Confirmed) からは InvalidPaymentStateTransition (IT8 US23)"):
    val pending = freshInvoice.issuePayment(LocalDate.parse("2026-10-31"), "REF1").value
    pending
      .issuePayment(LocalDate.parse("2026-11-30"), "REF2")
      .left
      .value shouldBe a[Invoice.InvalidPaymentStateTransition]

  test("confirmPayment: Pending → Confirmed 遷移 + paidAt 記録 (IT8 US23)"):
    val pending = freshInvoice.issuePayment(LocalDate.parse("2026-10-31"), "REF1").value
    val paid = Instant.parse("2026-10-15T09:00:00Z")
    val confirmed = pending.confirmPayment(paid).value
    confirmed.paymentStatus shouldBe PaymentStatus.Confirmed
    confirmed.paidAt shouldBe Some(paid)

  test("confirmPayment: Overdue からも Confirmed 遷移可能 (IT8 US23 / 期限超過後の入金許容)"):
    val pending = freshInvoice.issuePayment(LocalDate.parse("2026-10-31"), "REF1").value
    val overdue = pending.markOverdue(LocalDate.parse("2026-11-01")).value
    val confirmed = overdue.confirmPayment(Instant.parse("2026-11-05T09:00:00Z")).value
    confirmed.paymentStatus shouldBe PaymentStatus.Confirmed

  test("confirmPayment: NotIssued から直接 Confirmed は InvalidPaymentStateTransition (IT8 US23)"):
    freshInvoice.confirmPayment(now).left.value shouldBe a[Invoice.InvalidPaymentStateTransition]

  test("markOverdue: Pending + dueDate 超過時のみ Overdue 遷移、それ以外は InvalidPaymentStateTransition (IT8 US23)"):
    val pending = freshInvoice.issuePayment(LocalDate.parse("2026-10-31"), "REF1").value
    // 期限内: NG
    pending.markOverdue(LocalDate.parse("2026-10-15")).left.value shouldBe a[Invoice.InvalidPaymentStateTransition]
    // 期限超過: OK
    pending.markOverdue(LocalDate.parse("2026-11-01")).value.paymentStatus shouldBe PaymentStatus.Overdue
    // NotIssued から markOverdue: NG (dueDate 未設定)
    freshInvoice.markOverdue(LocalDate.parse("2026-12-31")).left.value shouldBe a[Invoice.InvalidPaymentStateTransition]
