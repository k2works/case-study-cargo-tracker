package cargotracker.billing.application.commandservices

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.repositories.InvoiceRepository
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, DiscountRate, InvoiceId}
import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus, CargoSpec, RouteSpecification}
import cargotracker.shared.domain.pricing.{InMemoryPricingService, PricingService}
import cargotracker.shared.domain.{CargoType, Location, Money, ShipperId, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Clock, Instant, LocalDate, ZoneId}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable

class BillingCommandServiceSpec extends AnyFunSuite with Matchers:

  private class InMemoryCargoRepo extends CargoRepository:
    val store: mutable.Map[BookingId, Cargo] = mutable.Map.empty
    override def findById(id: BookingId): Option[Cargo] = store.get(id)
    override def findAll(): Seq[Cargo] = store.values.toSeq
    override def findByStatus(status: BookingStatus): Seq[Cargo] =
      store.values.filter(_.status == status).toSeq
    override def save(c: Cargo): Unit = store.update(c.bookingId, c)
    override def nextIdentity(): BookingId = BookingId.unsafeFrom("BK-000001")

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

  private def cargoIn(status: BookingStatus): Cargo =
    val routeSpec = RouteSpecification(
      Location.unsafeFrom("JPYOK"),
      Location.unsafeFrom("USNYC"),
      LocalDate.of(2026, 12, 31)
    ).toOption.get
    val spec = CargoSpec(
      cargoType = CargoType.General,
      weight = Weight(1000).toOption.get,
      description = None,
      quantity = None,
      hazardous = None
    )
    Cargo.reconstruct(
      BookingId.unsafeFrom("BK-000001"),
      ShipperId.unsafeFrom("SH-000001"),
      routeSpec,
      spec,
      status
    )

  private def deliveredCargo: Cargo = cargoIn(BookingStatus.Delivered)

  test("generate: Delivered 予約から請求書を発行 Pending で永続化 (US21)"):
    val cargoRepo = new InMemoryCargoRepo
    val invRepo = new InMemoryInvoiceRepo
    val cargo = deliveredCargo
    cargoRepo.save(cargo)
    val service = new BillingCommandService(invRepo, cargoRepo, pricing, clock)
    val Right(inv) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    inv.paymentStatus shouldBe PaymentStatus.Pending
    inv.cargoBookingId.value shouldBe "BK-000001"
    invRepo.store should have size 1

  test("generate: Preliminary 予約は Delivered 必須エラー"):
    val cargoRepo = new InMemoryCargoRepo
    val invRepo = new InMemoryInvoiceRepo
    cargoRepo.save(cargoIn(BookingStatus.Preliminary))
    val service = new BillingCommandService(invRepo, cargoRepo, pricing, clock)
    val Left(msg) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    msg should include("Delivered")
    invRepo.store shouldBe empty

  test("generate: 既に発行済の予約は冪等成功 (既存 Invoice を返す)"):
    val cargoRepo = new InMemoryCargoRepo
    val invRepo = new InMemoryInvoiceRepo
    cargoRepo.save(deliveredCargo)
    val service = new BillingCommandService(invRepo, cargoRepo, pricing, clock)
    val Right(first) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    val Right(second) = service.generate(GenerateInvoiceCommand("BK-000001")): @unchecked
    first.invoiceId.value shouldBe second.invoiceId.value
    invRepo.store should have size 1
