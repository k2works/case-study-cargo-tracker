package cargotracker.billing.application.commandservices

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.repositories.{BillingCargoQueryPort, InvoiceRepository}
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, BillingCargoSnapshot, DiscountRate, InvoiceId}
import cargotracker.shared.domain.pricing.{InMemoryPricingService, PricingService}
import cargotracker.shared.domain.{CargoType, Location, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

class BillingCommandServiceSpec extends AnyFunSuite with Matchers:

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

  private def snapshot(isDelivered: Boolean, isCorporate: Boolean = false): BillingCargoSnapshot =
    BillingCargoSnapshot(
      bookingId = BillingBookingId.unsafeFrom("BK-000001"),
      shipperId = "SH-000001",
      isCorporate = isCorporate,
      isDelivered = isDelivered,
      origin = Location.unsafeFrom("JPYOK"),
      destination = Location.unsafeFrom("USNYC"),
      cargoType = CargoType.General,
      weight = Weight(1000).toOption.get,
      voyageNumbers = Nil
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
