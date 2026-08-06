package cargotracker.billing.infrastructure.repositories

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, BillingShipperId, DiscountRate}
import cargotracker.shared.domain.Money
import cargotracker.support.{DbCleanupSupport, PostgresContainerSupport}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}

import java.time.{Instant, LocalDate}

/** IT9 0.7: Invoice の IT8 / IT9 拡張列 (due_date / payment_reference / refunded_at / refund_reason) の永続化検証。
  *
  *   - PostgreSQL Testcontainers で実 DB に対して 4 列の SELECT/UPDATE が正しく動作することを検証
  *   - IT8 BillingCommandServiceSpec / InvoiceSpec の Unit テストでカバーされていない「実 DB との往復」を確認
  *   - V23 (due_date / payment_reference) + V29 (refunded_at / refund_reason) の Flyway 適用も間接的に検証される
  */
class ScalikeJdbcInvoiceRepositoryIntegrationSpec
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with OptionValues
    with PostgresContainerSupport
    with DbCleanupSupport:

  // invoice テーブルを TRUNCATE 対象に追加 (CASCADE で invoice_line_item / payment も連動)
  override protected def cleanupTables: Seq[String] = super.cleanupTables ++ Seq("invoice", "invoice_line_item")

  private def freshInvoice(suffix: String): Invoice =
    Invoice
      .issue(
        invoiceId = new ScalikeJdbcInvoiceRepository().nextInvoiceId(),
        cargoBookingId = BillingBookingId.unsafeFrom(s"BK-IT9$suffix"),
        shipperId = BillingShipperId(s"SH-IT9$suffix", isCorporate = true),
        baseAmount = Money.unsafeFromJpy(15300L),
        discountRate = DiscountRate(BigDecimal("0.15")).value,
        issuedAt = Instant.parse("2026-10-10T00:00:00Z")
      )
      .value

  test("save + findById: NotIssued 状態の Invoice を保存・復元できる (IT9 0.7)"):
    val repo = new ScalikeJdbcInvoiceRepository
    val inv = freshInvoice("01")
    repo.save(inv)
    val loaded = repo.findById(inv.invoiceId).value
    loaded.paymentStatus shouldBe PaymentStatus.NotIssued
    loaded.dueDate shouldBe None
    loaded.paymentReference shouldBe None
    loaded.refundedAt shouldBe None
    loaded.refundReason shouldBe None

  test("save (issuePayment 後): dueDate と paymentReference が永続化される (IT8 V23 / IT9 0.7)"):
    val repo = new ScalikeJdbcInvoiceRepository
    val inv = freshInvoice("02")
    repo.save(inv)
    val pending = inv.issuePayment(LocalDate.parse("2026-10-31"), "PAY-REF-IT9-02").value
    repo.save(pending)
    val loaded = repo.findById(inv.invoiceId).value
    loaded.paymentStatus shouldBe PaymentStatus.Pending
    loaded.dueDate shouldBe Some(LocalDate.parse("2026-10-31"))
    loaded.paymentReference shouldBe Some("PAY-REF-IT9-02")

  test("save (confirmPayment 後): paidAt が永続化される (IT8 V23 / IT9 0.7)"):
    val repo = new ScalikeJdbcInvoiceRepository
    val inv = freshInvoice("03")
    repo.save(inv)
    // 各 save 後に findById で再ロードして version を反映 (楽観ロック競合回避)
    val pending = inv.issuePayment(LocalDate.parse("2026-10-31"), "REF03").value
    repo.save(pending)
    val pendingReloaded = repo.findById(inv.invoiceId).value
    val confirmed = pendingReloaded.confirmPayment(Instant.parse("2026-10-15T09:00:00Z")).value
    repo.save(confirmed)
    val loaded = repo.findById(inv.invoiceId).value
    loaded.paymentStatus shouldBe PaymentStatus.Confirmed
    loaded.paidAt shouldBe Some(Instant.parse("2026-10-15T09:00:00Z"))

  test("save (refund 後): refundedAt と refundReason が永続化される (IT9 0.8 V29 / IT9 0.7)"):
    val repo = new ScalikeJdbcInvoiceRepository
    val inv = freshInvoice("04")
    repo.save(inv)
    val pending = inv.issuePayment(LocalDate.parse("2026-10-31"), "REF04").value
    repo.save(pending)
    val confirmed = repo.findById(inv.invoiceId).value.confirmPayment(Instant.parse("2026-10-15T09:00:00Z")).value
    repo.save(confirmed)
    val refunded = repo.findById(inv.invoiceId).value.refund(Instant.parse("2026-10-20T14:00:00Z"), "顧客都合キャンセル").value
    repo.save(refunded)
    val loaded = repo.findById(inv.invoiceId).value
    loaded.paymentStatus shouldBe PaymentStatus.Refunded
    loaded.refundedAt shouldBe Some(Instant.parse("2026-10-20T14:00:00Z"))
    loaded.refundReason shouldBe Some("顧客都合キャンセル")

  test("save: 楽観ロック - 同じ version の Invoice を 2 回 save すると 2 回目は OptimisticLockException (IT9 0.7)"):
    val repo = new ScalikeJdbcInvoiceRepository
    val inv = freshInvoice("05")
    repo.save(inv)
    val pending = inv.issuePayment(LocalDate.parse("2026-10-31"), "REF05").value
    repo.save(pending) // 1 回目 OK (version 0 → 1)
    // 2 回目: 古い inv (version 0 のまま) で save すると競合
    val anotherPending = inv.issuePayment(LocalDate.parse("2026-11-30"), "REF05B").value
    assertThrows[cargotracker.shared.domain.OptimisticLockException]:
      repo.save(anotherPending)
