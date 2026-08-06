package cargotracker.booking.domain.model.aggregates

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus, CargoSpec, RouteSpecification}
import cargotracker.shared.domain.{CargoType, Location, ShipperId, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate

/** Cargo.assignToRouting（US06）の遷移ルール検証。 */
class CargoAssignToRoutingSpec extends AnyFunSuite with Matchers:

  private val acceptingChecker: ShipperExistenceChecker = (_: ShipperId) => true
  private val spec = CargoSpec(
    cargoType = CargoType.General,
    weight = Weight(100).toOption.get,
    description = None,
    quantity = None,
    hazardous = None
  )
  private val routeSpec = RouteSpecification(
    Location.unsafeFrom("JPYOK"),
    Location.unsafeFrom("USNYC"),
    LocalDate.now.plusDays(30)
  ).toOption.get

  private def newCargo(): Cargo = Cargo
    .book(
      BookingId.unsafeFrom("BK-000001"),
      ShipperId.unsafeFrom("SH-000001"),
      routeSpec,
      spec,
      acceptingChecker
    )
    .toOption
    .get

  test("Preliminary 状態の予約は RouteProposed に遷移できる"):
    val Right(updated) = newCargo().assignToRouting(): @unchecked
    updated.status shouldBe BookingStatus.RouteProposed

  test("既に RouteProposed の予約を再度引き渡そうとすると InvalidStatusTransition"):
    val Right(once) = newCargo().assignToRouting(): @unchecked
    once.assignToRouting() shouldBe Left(
      Cargo.InvalidStatusTransition(BookingStatus.RouteProposed, BookingStatus.RouteProposed)
    )

  test("Cancelled 状態からは RouteProposed へ遷移できない"):
    val cancelled =
      Cargo.reconstruct(
        Cargo.Snapshot(
          BookingId.unsafeFrom("BK-000099"),
          ShipperId.unsafeFrom("SH-000001"),
          routeSpec,
          spec,
          BookingStatus.Cancelled
        )
      )
    val Left(err) = cancelled.assignToRouting(): @unchecked
    err shouldBe Cargo.InvalidStatusTransition(
      BookingStatus.Cancelled,
      BookingStatus.RouteProposed
    )

  test("BookingStatus.canTransitionTo の代表ケース"):
    BookingStatus.Preliminary.canTransitionTo(BookingStatus.RouteProposed) shouldBe true
    // IT4 US11/US13: RouteProposed → Confirmed は直接遷移せず RouteAssigned を経由する
    BookingStatus.RouteProposed.canTransitionTo(BookingStatus.Confirmed) shouldBe false
    BookingStatus.RouteProposed.canTransitionTo(BookingStatus.RouteAssigned) shouldBe true
    BookingStatus.RouteAssigned.canTransitionTo(BookingStatus.Confirmed) shouldBe true
    BookingStatus.Preliminary.canTransitionTo(BookingStatus.Confirmed) shouldBe false
    BookingStatus.Cancelled.canTransitionTo(BookingStatus.Preliminary) shouldBe false
    BookingStatus.Confirmed.canTransitionTo(BookingStatus.Cancelled) shouldBe true
