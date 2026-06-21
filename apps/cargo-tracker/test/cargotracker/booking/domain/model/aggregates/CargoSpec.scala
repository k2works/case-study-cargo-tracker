package cargotracker.booking.domain.model.aggregates

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.valueobjects.{
  BookingId,
  BookingStatus,
  CargoSpec,
  Itinerary,
  RouteSpecification
}
import cargotracker.shared.domain.{CargoType, Location, ShipperId, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate
class CargoBookingSpec extends AnyFunSuite with Matchers:

  private val shipperId = ShipperId.unsafeFrom("SH-000001")
  private val bookingId = BookingId.unsafeFrom("BK-000001")
  private val routeSpec = RouteSpecification(
    Location.unsafeFrom("JPYOK"),
    Location.unsafeFrom("USNYC"),
    LocalDate.now.plusDays(30)
  ).toOption.get
  private val spec = CargoSpec(
    cargoType = CargoType.General,
    weight = Weight(100).toOption.get,
    description = Some("test"),
    quantity = Some(1),
    hazardous = None
  )

  private val acceptingChecker: ShipperExistenceChecker =
    (_: ShipperId) => true
  private val rejectingChecker: ShipperExistenceChecker =
    (_: ShipperId) => false

  test("既知の荷主で予約を生成すると Preliminary 状態になる"):
    val res = Cargo.book(bookingId, shipperId, routeSpec, spec, acceptingChecker)
    res.isRight shouldBe true
    res.toOption.get.status shouldBe BookingStatus.Preliminary

  test("未知の荷主の予約は UnknownShipper を返す"):
    Cargo.book(
      bookingId,
      shipperId,
      routeSpec,
      spec,
      rejectingChecker
    ) shouldBe Left(Cargo.UnknownShipper)

  test("RouteSpecification は出発地と目的地が同一の場合に拒否される"):
    val same = RouteSpecification(
      Location.unsafeFrom("JPYOK"),
      Location.unsafeFrom("JPYOK"),
      LocalDate.now
    )
    same shouldBe Left(RouteSpecification.SameOriginAndDestination)

  test("assignItinerary: RouteProposed の予約に経路を紐付けて RouteAssigned に遷移"):
    val Right(pre) = Cargo.book(bookingId, shipperId, routeSpec, spec, acceptingChecker): @unchecked
    val Right(routed) = pre.assignToRouting(): @unchecked
    val itinerary = Itinerary.unsafeFrom(List("VY-1", "VY-2"))
    val Right(assigned) = routed.assignItinerary(itinerary): @unchecked
    assigned.status shouldBe BookingStatus.RouteAssigned
    assigned.itinerary shouldBe Some(itinerary)

  test("assignItinerary: Preliminary からの呼び出しは InvalidStatusTransition"):
    val Right(pre) = Cargo.book(bookingId, shipperId, routeSpec, spec, acceptingChecker): @unchecked
    pre.assignItinerary(Itinerary.unsafeFrom(List("VY-1"))) shouldBe
      Left(Cargo.InvalidStatusTransition(BookingStatus.Preliminary, BookingStatus.RouteAssigned))
