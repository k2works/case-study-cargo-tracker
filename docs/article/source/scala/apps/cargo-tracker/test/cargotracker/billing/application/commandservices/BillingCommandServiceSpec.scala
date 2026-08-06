package cargotracker.billing.application.commandservices

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.ports.MailNotificationPort
import cargotracker.billing.domain.model.repositories.{BillingCargoQueryPort, InvoiceRepository}
import cargotracker.billing.domain.model.valueobjects.{
  BillingBookingId,
  BillingCargoSnapshot,
  DiscountRate,
  InvoiceId,
  LineItemCategory
}
import cargotracker.booking.application.api.BookingPublicApi
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.shared.domain.pricing.{InMemoryPricingService, PricingService}
import cargotracker.shared.domain.{CargoType, Location, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}

import java.time.{Clock, Instant, LocalDate, ZoneId}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

class BillingCommandServiceSpec extends AnyFunSuite with Matchers with EitherValues with OptionValues:

  private class FakeBillingCargoQueryPort extends BillingCargoQueryPort:
    val store: mutable.Map[String, BillingCargoSnapshot] = mutable.Map.empty
    override def findForBilling(bookingId: BillingBookingId): Option[BillingCargoSnapshot] =
      store.get(bookingId.value)

  /** Fake BookingPublicApi (IT8 US23): 呼出ログを記録するだけのテストダブル。 */
  private class FakeBookingPublicApi extends BookingPublicApi:
    val paymentRequested: mutable.Buffer[(String, String, String, String, Long)] = mutable.Buffer.empty
    val paymentConfirmed: mutable.Buffer[(String, String, String, Long)] = mutable.Buffer.empty
    val overdueAlerted: mutable.Buffer[(String, String, String, Long)] = mutable.Buffer.empty
    override def logHandlingNotification(b: String, t: String, e: String, l: String): Either[String, Unit] = Right(())
    override def completeDelivery(b: String, t: String, l: String, r: String): Either[String, Cargo] =
      Left("not used in spec")
    override def logPaymentRequested(b: String, i: String, d: String, p: String, a: Long): Either[String, Unit] =
      paymentRequested += ((b, i, d, p, a)); Right(())
    override def logPaymentConfirmed(b: String, i: String, p: String, a: Long): Either[String, Unit] =
      paymentConfirmed += ((b, i, p, a)); Right(())
    override def logOverdueAlerted(b: String, i: String, d: String, a: Long): Either[String, Unit] =
      overdueAlerted += ((b, i, d, a)); Right(())
    val settled: mutable.Buffer[String] = mutable.Buffer.empty
    override def markSettled(bookingId: String): Either[String, Cargo] =
      settled += bookingId; Left("not used in spec (テストは記録ログのみ確認)")

  /** Noop MailNotificationPort (IT8 US23): メール送信をスキップするテストダブル。 */
  private class NoopMail extends MailNotificationPort:
    val sentRequested: mutable.Buffer[(String, String, String, String, Long)] = mutable.Buffer.empty
    val sentConfirmed: mutable.Buffer[(String, String, String, Long)] = mutable.Buffer.empty
    val sentOverdue: mutable.Buffer[(String, String, String, Long)] = mutable.Buffer.empty
    override def sendPaymentRequested(b: String, i: String, d: String, p: String, a: Long): Either[String, Unit] =
      sentRequested += ((b, i, d, p, a)); Right(())
    override def sendPaymentConfirmed(b: String, i: String, p: String, a: Long): Either[String, Unit] =
      sentConfirmed += ((b, i, p, a)); Right(())
    override def sendOverdueAlert(b: String, i: String, d: String, a: Long): Either[String, Unit] =
      sentOverdue += ((b, i, d, a)); Right(())

  private class InMemoryInvoiceRepo extends InvoiceRepository:
    val store: mutable.Map[String, Invoice] = mutable.Map.empty
    private val seq = AtomicLong(0L)
    override def nextInvoiceId(): InvoiceId = InvoiceId.fromSequence(seq.incrementAndGet())
    override def findById(id: InvoiceId): Option[Invoice] = store.get(id.value)
    override def findByBookingId(bid: BillingBookingId): Option[Invoice] =
      store.values.find(_.cargoBookingId.value == bid.value)
    override def findAll(): Seq[Invoice] = store.values.toSeq
    override def save(inv: Invoice): Unit = store.update(inv.invoiceId.value, inv)

  private val clock = Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneId.of("UTC"))
  private val pricing: PricingService = new InMemoryPricingService

  private def snapshot(
      isDelivered: Boolean,
      isCorporate: Boolean = false,
      corporateDiscountRate: Option[BigDecimal] = None
  ): BillingCargoSnapshot =
    BillingCargoSnapshot(
      bookingId = BillingBookingId.unsafeFrom("BK-000001"),
      shipperId = "SH-000001",
      isCorporate = isCorporate,
      isDelivered = isDelivered,
      origin = Location.unsafeFrom("JPYOK"),
      destination = Location.unsafeFrom("USNYC"),
      cargoType = CargoType.General,
      weight = Weight(1000).toOption.get,
      voyageNumbers = Nil,
      corporateDiscountRate = corporateDiscountRate
    )

  test("generate: Delivered 予約から請求書を発行 Pending で永続化 (US21)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    val inv = service.generate(GenerateInvoiceCommand("BK-000001")).value
    // IT8 ADR 0019 (案 B): 発行直後は NotIssued、issuePayment 経由で Pending に遷移する
    inv.paymentStatus shouldBe PaymentStatus.NotIssued
    inv.cargoBookingId.value shouldBe "BK-000001"
    invRepo.store should have size 1

  test("generate: Preliminary 予約は Delivered 必須エラー"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = false))
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    val msg = service.generate(GenerateInvoiceCommand("BK-000001")).left.value
    msg should include("Delivered")
    invRepo.store shouldBe empty

  test("generate: 既に発行済の予約は冪等成功 (既存 Invoice を返す)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    val first = service.generate(GenerateInvoiceCommand("BK-000001")).value
    val second = service.generate(GenerateInvoiceCommand("BK-000001")).value
    first.invoiceId.value shouldBe second.invoiceId.value
    invRepo.store should have size 1

  test("generate: 法人荷主スナップショットから発行された Invoice の shipperId.isCorporate=true (IT7 0.8 / H5)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = true, isCorporate = true))
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    val inv = service.generate(GenerateInvoiceCommand("BK-000001")).value
    inv.shipperId.isCorporate shouldBe true

  test("generate: 個人荷主スナップショットから発行された Invoice の shipperId.isCorporate=false"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = true, isCorporate = false))
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    val inv = service.generate(GenerateInvoiceCommand("BK-000001")).value
    inv.shipperId.isCorporate shouldBe false

  // IT8 US22: 法人割引適用シナリオ 3 件 + Discount 明細

  test("generate: 個人荷主 (corporateDiscountRate=None) は割引 0% で発行される (IT8 US22)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = true, isCorporate = false, corporateDiscountRate = None))
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    val inv = service.generate(GenerateInvoiceCommand("BK-000001")).value
    inv.discountRate.value shouldBe BigDecimal(0)
    inv.finalAmount.amount shouldBe inv.baseAmount.amount
    inv.lineItems.exists(_.category == LineItemCategory.Discount) shouldBe false

  test("generate: 法人荷主 (corporateDiscountRate=0.15) は 15% 割引適用 + Discount 明細追加 (IT8 US22)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update(
      "BK-000001",
      snapshot(isDelivered = true, isCorporate = true, corporateDiscountRate = Some(BigDecimal("0.15")))
    )
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    val inv = service.generate(GenerateInvoiceCommand("BK-000001")).value
    inv.discountRate.value shouldBe BigDecimal("0.15")
    inv.finalAmount.amount shouldBe (inv.baseAmount.amount * 85 / 100)
    val discountItem = inv.lineItems.find(_.category == LineItemCategory.Discount).value
    discountItem.name should include("15%")
    discountItem.amount.amount should be < 0L

  test("generate: 法人荷主 (corporateDiscountRate=0.30) は最大割引 30% 適用 (IT8 US22 境界値)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update(
      "BK-000001",
      snapshot(isDelivered = true, isCorporate = true, corporateDiscountRate = Some(BigDecimal("0.30")))
    )
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    val inv = service.generate(GenerateInvoiceCommand("BK-000001")).value
    inv.discountRate.value shouldBe BigDecimal("0.30")
    inv.finalAmount.amount shouldBe (inv.baseAmount.amount * 70 / 100)
    inv.lineItems.find(_.category == LineItemCategory.Discount).value.name should include("30%")

  test("generate: snapshot.corporateDiscountRate は command.discountRate より優先される (IT8 US22 UI 入力依存ゼロ)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update(
      "BK-000001",
      snapshot(isDelivered = true, isCorporate = true, corporateDiscountRate = Some(BigDecimal("0.20")))
    )
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    // command 側で 0.05 を指定しても、snapshot 側の 0.20 が優先される
    val inv = service
      .generate(GenerateInvoiceCommand("BK-000001", Some(DiscountRate(BigDecimal("0.05")).toOption.get)))
      .value
    inv.discountRate.value shouldBe BigDecimal("0.20")

  // IT8 US23 タスク 2.4: issuePayment

  test(
    "issuePayment: 確定 Invoice (NotIssued) を Pending に遷移 + dueDate/paymentReference 設定 + PaymentRequested 通知記録 (IT8 US23)"
  ):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, booking, new NoopMail, clock)
    val invoice = service.generate(GenerateInvoiceCommand("BK-000001")).value
    val due = LocalDate.parse("2026-10-31")
    val updated = service
      .issuePayment(IssuePaymentCommand(invoice.invoiceId.value, due, "PAY-REF-001"))
      .value
    updated.paymentStatus shouldBe PaymentStatus.Pending
    updated.dueDate shouldBe Some(due)
    updated.paymentReference shouldBe Some("PAY-REF-001")
    booking.paymentRequested should have size 1
    val (b, inv, d, ref, amt) = booking.paymentRequested.head
    b shouldBe "BK-000001"
    inv shouldBe updated.invoiceId.value
    d shouldBe "2026-10-31"
    ref shouldBe "PAY-REF-001"
    amt shouldBe updated.finalAmount.amount

  test("issuePayment: 不正な状態遷移 (Pending → 再 issue) は Left (IT8 US23)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, booking, new NoopMail, clock)
    val invoice = service.generate(GenerateInvoiceCommand("BK-000001")).value
    service
      .issuePayment(IssuePaymentCommand(invoice.invoiceId.value, LocalDate.parse("2026-10-31"), "REF1"))
      .value
    val msg = service
      .issuePayment(IssuePaymentCommand(invoice.invoiceId.value, LocalDate.parse("2026-11-30"), "REF2"))
      .left
      .value
    msg should include("支払発行可能な状態ではありません")
    booking.paymentRequested should have size 1 // 2回目は通知記録されない

  test("issuePayment: 不明な invoice は Left (IT8 US23)"):
    val service = new BillingCommandService(
      new InMemoryInvoiceRepo,
      new FakeBillingCargoQueryPort,
      pricing,
      new FakeBookingPublicApi,
      new NoopMail,
      clock
    )
    val msg = service
      .issuePayment(IssuePaymentCommand("INV-999999", LocalDate.parse("2026-10-31"), "X"))
      .left
      .value
    msg should include("見つかりません")

  // IT8 US23 タスク 2.5: confirmPayment

  test(
    "confirmPayment: Pending Invoice を Confirmed に遷移 + paidAt 記録 + Cargo.markSettled + PaymentConfirmed 通知 (IT8 US23)"
  ):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, booking, new NoopMail, clock)
    val invoice = service.generate(GenerateInvoiceCommand("BK-000001")).value
    service
      .issuePayment(IssuePaymentCommand(invoice.invoiceId.value, LocalDate.parse("2026-10-31"), "REF1"))
      .value
    val paid = Instant.parse("2026-10-15T09:00:00Z")
    val confirmed = service.confirmPayment(ConfirmPaymentCommand(invoice.invoiceId.value, paid)).value
    confirmed.paymentStatus shouldBe PaymentStatus.Confirmed
    confirmed.paidAt shouldBe Some(paid)
    booking.settled should contain("BK-000001")
    booking.paymentConfirmed should have size 1

  test("confirmPayment: NotIssued Invoice は不正状態遷移で Left (IT8 US23)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, booking, new NoopMail, clock)
    val invoice = service.generate(GenerateInvoiceCommand("BK-000001")).value
    val msg = service.confirmPayment(ConfirmPaymentCommand(invoice.invoiceId.value, clock.instant())).left.value
    msg should include("入金確認可能な状態ではありません")
    booking.settled shouldBe empty
    booking.paymentConfirmed shouldBe empty

  // IT8 US23 タスク 2.6: detectOverdue

  test("detectOverdue: 期限超過 Pending Invoice を Overdue 化 + OverdueAlerted 通知 (IT8 US23)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, booking, new NoopMail, clock)
    val invoice = service.generate(GenerateInvoiceCommand("BK-000001")).value
    service
      .issuePayment(IssuePaymentCommand(invoice.invoiceId.value, LocalDate.parse("2026-10-31"), "REF1"))
      .value
    // 期限超過判定
    val count = service.detectOverdue(LocalDate.parse("2026-11-15"))
    count shouldBe 1
    invRepo.store(invoice.invoiceId.value).paymentStatus shouldBe PaymentStatus.Overdue
    booking.overdueAlerted should have size 1

  test("detectOverdue: 期限内 Pending Invoice はスキップ (count=0) (IT8 US23)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, booking, new NoopMail, clock)
    val invoice = service.generate(GenerateInvoiceCommand("BK-000001")).value
    service
      .issuePayment(IssuePaymentCommand(invoice.invoiceId.value, LocalDate.parse("2026-12-31"), "REF1"))
      .value
    val count = service.detectOverdue(LocalDate.parse("2026-11-15"))
    count shouldBe 0
    invRepo.store(invoice.invoiceId.value).paymentStatus shouldBe PaymentStatus.Pending
    booking.overdueAlerted shouldBe empty

  // IT8 US23 タスク 2.10: MailNotificationPort 送信検証 (NoopMail バッファ確認)

  test("issuePayment: MailNotificationPort.sendPaymentRequested が呼び出される (IT8 US23 2.9/2.10)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    val mail = new NoopMail
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, booking, mail, clock)
    val invoice = service.generate(GenerateInvoiceCommand("BK-000001")).value
    service
      .issuePayment(IssuePaymentCommand(invoice.invoiceId.value, LocalDate.parse("2026-10-31"), "PAY-REF-001"))
      .value
    mail.sentRequested should have size 1
    val (b, inv, d, ref, amt) = mail.sentRequested.head
    b shouldBe "BK-000001"
    inv shouldBe invoice.invoiceId.value
    d shouldBe "2026-10-31"
    ref shouldBe "PAY-REF-001"
    amt shouldBe invoice.finalAmount.amount

  test("confirmPayment: MailNotificationPort.sendPaymentConfirmed が呼び出される (IT8 US23 2.9/2.10)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    val mail = new NoopMail
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, booking, mail, clock)
    val invoice = service.generate(GenerateInvoiceCommand("BK-000001")).value
    service.issuePayment(IssuePaymentCommand(invoice.invoiceId.value, LocalDate.parse("2026-10-31"), "R1")).value
    val paid = Instant.parse("2026-10-15T09:00:00Z")
    service.confirmPayment(ConfirmPaymentCommand(invoice.invoiceId.value, paid)).value
    mail.sentConfirmed should have size 1
    mail.sentConfirmed.head._3 shouldBe paid.toString

  test("detectOverdue: MailNotificationPort.sendOverdueAlert が呼び出される (IT8 US23 2.9/2.10)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    val mail = new NoopMail
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, booking, mail, clock)
    val invoice = service.generate(GenerateInvoiceCommand("BK-000001")).value
    service.issuePayment(IssuePaymentCommand(invoice.invoiceId.value, LocalDate.parse("2026-10-31"), "R1")).value
    service.detectOverdue(LocalDate.parse("2026-11-15"))
    mail.sentOverdue should have size 1
    mail.sentOverdue.head._3 shouldBe "2026-10-31"

  // IT9 US29: confirmPaymentsBatch (CSV 取込)

  test("confirmPaymentsBatch: 4 行 (成功 2 + 不一致 1 + 二重 1) で正しく分類される (IT9 US29)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val booking = new FakeBookingPublicApi
    port.store.update(
      "BK-IT9CSV",
      snapshot(isDelivered = true).copy(bookingId = BillingBookingId.unsafeFrom("BK-IT9CSV"))
    )
    val service = new BillingCommandService(invRepo, port, pricing, booking, new NoopMail, clock)
    val inv1 = service.generate(GenerateInvoiceCommand("BK-IT9CSV")).value
    service.issuePayment(IssuePaymentCommand(inv1.invoiceId.value, LocalDate.parse("2026-10-31"), "PAY-001")).value
    // 別 invoice 用にもう 1 件
    port.store.update(
      "BK-IT9CSV2",
      snapshot(isDelivered = true).copy(bookingId = BillingBookingId.unsafeFrom("BK-IT9CSV2"))
    )
    val inv2 = service.generate(GenerateInvoiceCommand("BK-IT9CSV2")).value
    service.issuePayment(IssuePaymentCommand(inv2.invoiceId.value, LocalDate.parse("2026-10-31"), "PAY-002")).value
    // 3 件目: 既 Confirmed の Invoice
    port.store.update(
      "BK-IT9CSV3",
      snapshot(isDelivered = true).copy(bookingId = BillingBookingId.unsafeFrom("BK-IT9CSV3"))
    )
    val inv3 = service.generate(GenerateInvoiceCommand("BK-IT9CSV3")).value
    service.issuePayment(IssuePaymentCommand(inv3.invoiceId.value, LocalDate.parse("2026-10-31"), "PAY-003")).value
    service.confirmPayment(ConfirmPaymentCommand(inv3.invoiceId.value, Instant.parse("2026-10-10T09:00:00Z"))).value

    val rows = Seq(
      CsvPaymentInput("PAY-001", Instant.parse("2026-10-15T09:00:00Z"), 15300L),
      CsvPaymentInput("PAY-002", Instant.parse("2026-10-15T10:00:00Z"), 15300L),
      CsvPaymentInput("PAY-NOTFOUND", Instant.parse("2026-10-15T11:00:00Z"), 9999L),
      CsvPaymentInput("PAY-003", Instant.parse("2026-10-15T12:00:00Z"), 15300L)
    )
    val result = service.confirmPaymentsBatch(rows)
    result.totalRows shouldBe 4
    result.succeeded shouldBe 2
    result.unmatched should have size 1
    result.unmatched.head.referenceCode shouldBe "PAY-NOTFOUND"
    result.alreadyConfirmed should have size 1
    result.alreadyConfirmed.head._1.referenceCode shouldBe "PAY-003"
    result.alreadyConfirmed.head._2 shouldBe "Confirmed"
    result.errors shouldBe empty

  test("confirmPaymentsBatch: 全件不一致なら succeeded=0 + unmatched=全行 (IT9 US29)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    val service = new BillingCommandService(invRepo, port, pricing, new FakeBookingPublicApi, new NoopMail, clock)
    val rows = Seq(
      CsvPaymentInput("UNKNOWN-1", Instant.parse("2026-10-15T09:00:00Z"), 1000L),
      CsvPaymentInput("UNKNOWN-2", Instant.parse("2026-10-15T10:00:00Z"), 2000L)
    )
    val result = service.confirmPaymentsBatch(rows)
    result.succeeded shouldBe 0
    result.unmatched should have size 2
    result.alreadyConfirmed shouldBe empty
