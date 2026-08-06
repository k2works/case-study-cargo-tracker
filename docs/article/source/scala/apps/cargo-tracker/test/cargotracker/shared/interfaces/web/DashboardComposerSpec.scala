package cargotracker.shared.interfaces.web

import cargotracker.auth.domain.model.valueobjects.Role
import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.valueobjects.{BookingId, CargoSpec, RouteSpecification}
import cargotracker.shared.domain.{CargoType, Location, ShipperId, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate

/** DashboardComposer の pure function テスト（IT3 タスク 0.6）。 */
class DashboardComposerSpec extends AnyFunSuite with Matchers:

  private val acceptingChecker: ShipperExistenceChecker = (_: ShipperId) => true
  private val sampleCargo = Cargo
    .book(
      BookingId.unsafeFrom("BK-DASH01"),
      ShipperId.unsafeFrom("SH-000001"),
      RouteSpecification(
        Location.unsafeFrom("JPTYO"),
        Location.unsafeFrom("USLAX"),
        LocalDate.now.plusDays(30)
      ).toOption.get,
      CargoSpec(
        cargoType = CargoType.General,
        weight = Weight(100).toOption.get,
        description = None,
        quantity = None,
        hazardous = None
      ),
      acceptingChecker
    )
    .toOption
    .get

  test("shouldFetchRouteProposed: RouteDesigner ロールなら true"):
    DashboardComposer.shouldFetchRouteProposed(Set(Role.RouteDesigner)) shouldBe true

  test("shouldFetchRouteProposed: MasterAdmin ロールなら true"):
    DashboardComposer.shouldFetchRouteProposed(Set(Role.MasterAdmin)) shouldBe true

  test("shouldFetchRouteProposed: Sales のみなら false"):
    DashboardComposer.shouldFetchRouteProposed(Set(Role.Sales)) shouldBe false

  test("shouldFetchRouteProposed: 空ロールなら false"):
    DashboardComposer.shouldFetchRouteProposed(Set.empty) shouldBe false

  test("compose: RouteDesigner には fetchRouteProposed が呼び出され結果が反映される"):
    val view = DashboardComposer.compose(
      username = "designer",
      roles = Set(Role.RouteDesigner),
      fetchRouteProposed = () => Seq(sampleCargo)
    )
    view.routeProposed should have size 1
    view.canSeeRouting shouldBe true
    view.canSeeSales shouldBe false

  test("compose: Sales のみなら fetchRouteProposed は呼ばれず空 Seq"):
    val called = new java.util.concurrent.atomic.AtomicBoolean(false)
    val view = DashboardComposer.compose(
      username = "sales",
      roles = Set(Role.Sales),
      fetchRouteProposed = () =>
        called.set(true)
        Seq(sampleCargo)
    )
    called.get shouldBe false
    view.routeProposed shouldBe empty
    view.canSeeSales shouldBe true

  test("shouldFetchRouteAssigned: Sales ロールなら true"):
    DashboardComposer.shouldFetchRouteAssigned(Set(Role.Sales)) shouldBe true

  test("shouldFetchRouteAssigned: RouteDesigner のみなら false"):
    DashboardComposer.shouldFetchRouteAssigned(Set(Role.RouteDesigner)) shouldBe false

  test("compose: Sales には fetchRouteAssigned が呼び出され結果が反映される"):
    val view = DashboardComposer.compose(
      username = "sales",
      roles = Set(Role.Sales),
      fetchRouteProposed = () => Seq.empty,
      fetchRouteAssigned = () => Seq(sampleCargo)
    )
    view.routeAssigned should have size 1

  test("compose: RouteDesigner のみなら fetchRouteAssigned は呼ばれず空 Seq"):
    val called = new java.util.concurrent.atomic.AtomicBoolean(false)
    val view = DashboardComposer.compose(
      username = "designer",
      roles = Set(Role.RouteDesigner),
      fetchRouteProposed = () => Seq.empty,
      fetchRouteAssigned = () =>
        called.set(true)
        Seq(sampleCargo)
    )
    called.get shouldBe false
    view.routeAssigned shouldBe empty

  test("canSee 系: MasterAdmin は全カードに権限を持つ"):
    val view = DashboardComposer.compose("admin", Set(Role.MasterAdmin), () => Seq.empty)
    view.canSeeSales shouldBe true
    view.canSeeRouting shouldBe true
    view.canSeeTracking shouldBe true
    view.canSeeSettlement shouldBe true
    view.canSeeMasterAdmin shouldBe true
