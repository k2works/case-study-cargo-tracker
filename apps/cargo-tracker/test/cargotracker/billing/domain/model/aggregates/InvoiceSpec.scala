package cargotracker.billing.domain.model.aggregates

import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, BillingShipperId, DiscountRate, InvoiceId}
import cargotracker.shared.domain.Money
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class InvoiceSpec extends AnyFunSuite with Matchers:

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
    inv.paymentStatus shouldBe PaymentStatus.Pending

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
