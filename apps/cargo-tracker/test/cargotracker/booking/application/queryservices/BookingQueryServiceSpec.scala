package cargotracker.booking.application.queryservices

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{BookingId, CargoSpec, RouteSpecification}
import cargotracker.shared.domain.{CargoType, Location, ShipperId, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate
import scala.collection.mutable

class BookingQueryServiceSpec extends AnyFunSuite with Matchers:

  private val routeSpec = RouteSpecification(
    Location.unsafeFrom("JPYOK"),
    Location.unsafeFrom("USNYC"),
    LocalDate.now.plusDays(30)
  ).toOption.get

  private val spec = CargoSpec(
    cargoType = CargoType.General,
    weight = Weight(100).toOption.get,
    description = None,
    quantity = None,
    hazardous = None
  )

  private val cargo = Cargo
    .book(
      BookingId.unsafeFrom("BK-000001"),
      ShipperId.unsafeFrom("SH-000001"),
      routeSpec,
      spec,
      (_: ShipperId) => true
    )
    .toOption
    .get

  private class InMemoryCargoRepository(seed: Cargo*) extends CargoRepository:
    private val store = mutable.Map.from(seed.map(c => c.bookingId -> c))
    override def findById(id: BookingId): Option[Cargo] = store.get(id)
    override def findAll(): Seq[Cargo] = store.values.toSeq
    override def findByStatus(
        status: cargotracker.booking.domain.model.valueobjects.BookingStatus
    ): Seq[Cargo] = store.values.filter(_.status == status).toSeq
    override def save(c: Cargo): Unit = store.update(c.bookingId, c)
    override def nextIdentity(): BookingId = BookingId.unsafeFrom("BK-999999")

  test("findAll はリポジトリの全件を返す"):
    val service = new BookingQueryService(new InMemoryCargoRepository(cargo))
    service.findAll() should contain only cargo

  test("findById は文字列 ID で予約を取得する"):
    val service = new BookingQueryService(new InMemoryCargoRepository(cargo))
    service.findById("BK-000001") shouldBe Some(cargo)

  test("findById は未知の ID で None を返す"):
    val service = new BookingQueryService(new InMemoryCargoRepository(cargo))
    service.findById("BK-999999") shouldBe None

  test("findRouteProposed は RouteProposed のみ返す（US06）"):
    val routed = Cargo.reconstruct(
      BookingId.unsafeFrom("BK-000002"),
      cargotracker.shared.domain.ShipperId.unsafeFrom("SH-000001"),
      cargo.routeSpecification,
      cargo.cargoSpec,
      cargotracker.booking.domain.model.valueobjects.BookingStatus.RouteProposed
    )
    val service = new BookingQueryService(new InMemoryCargoRepository(cargo, routed))
    service.findRouteProposed().map(_.bookingId.value) shouldBe Seq("BK-000002")
