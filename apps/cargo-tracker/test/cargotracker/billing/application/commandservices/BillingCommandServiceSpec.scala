package cargotracker.billing.application.commandservices

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.repositories.{BillingCargoQueryPort, InvoiceRepository}
import cargotracker.billing.domain.model.valueobjects.{
  BillingBookingId,
  BillingCargoSnapshot,
  DiscountRate,
  InvoiceId,
  LineItemCategory
}
import cargotracker.shared.domain.pricing.{InMemoryPricingService, PricingService}
import cargotracker.shared.domain.{CargoType, Location, Weight}
import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

class BillingCommandServiceSpec extends AnyFunSuite with Matchers with EitherValues with OptionValues:

  private class FakeBillingCargoQueryPort extends BillingCargoQueryPort:
    val store: mutable.Map[String, BillingCargoSnapshot] = mutable.Map.empty
    override def findForBilling(bookingId: BillingBookingId): Option[BillingCargoSnapshot] =
      store.get(bookingId.value)

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
    val service = new BillingCommandService(invRepo, port, pricing, clock)
    val Right(inv) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    inv.paymentStatus shouldBe PaymentStatus.Pending
    inv.cargoBookingId.value shouldBe "BK-000001"
    invRepo.store should have size 1

  test("generate: Preliminary 予約は Delivered 必須エラー"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = false))
    val service = new BillingCommandService(invRepo, port, pricing, clock)
    val Left(msg) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    msg should include("Delivered")
    invRepo.store shouldBe empty

  test("generate: 既に発行済の予約は冪等成功 (既存 Invoice を返す)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = true))
    val service = new BillingCommandService(invRepo, port, pricing, clock)
    val Right(first) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    val Right(second) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    first.invoiceId.value shouldBe second.invoiceId.value
    invRepo.store should have size 1

  test("generate: 法人荷主スナップショットから発行された Invoice の shipperId.isCorporate=true (IT7 0.8 / H5)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = true, isCorporate = true))
    val service = new BillingCommandService(invRepo, port, pricing, clock)
    val Right(inv) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    inv.shipperId.isCorporate shouldBe true

  test("generate: 個人荷主スナップショットから発行された Invoice の shipperId.isCorporate=false"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = true, isCorporate = false))
    val service = new BillingCommandService(invRepo, port, pricing, clock)
    val Right(inv) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    inv.shipperId.isCorporate shouldBe false

  // IT8 US22: 法人割引適用シナリオ 3 件 + Discount 明細

  test("generate: 個人荷主 (corporateDiscountRate=None) は割引 0% で発行される (IT8 US22)"):
    val port = new FakeBillingCargoQueryPort
    val invRepo = new InMemoryInvoiceRepo
    port.store.update("BK-000001", snapshot(isDelivered = true, isCorporate = false, corporateDiscountRate = None))
    val service = new BillingCommandService(invRepo, port, pricing, clock)
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
    val service = new BillingCommandService(invRepo, port, pricing, clock)
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
    val service = new BillingCommandService(invRepo, port, pricing, clock)
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
    val service = new BillingCommandService(invRepo, port, pricing, clock)
    // command 側で 0.05 を指定しても、snapshot 側の 0.20 が優先される
    val inv = service
      .generate(GenerateInvoiceCommand("BK-000001", Some(DiscountRate(BigDecimal("0.05")).toOption.get)))
      .value
    inv.discountRate.value shouldBe BigDecimal("0.20")
