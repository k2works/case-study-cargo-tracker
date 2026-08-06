package cargotracker.booking.infrastructure.repositories

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.valueobjects.{
  BookingId,
  CargoSpec,
  HazardousDeclaration,
  RefrigerationSpec,
  RouteSpecification,
  TemperatureUnit
}
import cargotracker.shared.domain.{CargoType, Location, ShipperId, Weight}
import cargotracker.support.{DbCleanupSupport, PostgresContainerSupport}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate

/** ScalikeJdbcCargoRepository の冷凍貨物マッピング統合テスト（US05）。 V6 マイグレーション後に refrigeration カラムが round-trip することを確認する。 */
class ScalikeJdbcCargoRepositorySpec
    extends AnyFunSuite
    with Matchers
    with PostgresContainerSupport
    with DbCleanupSupport:

  private val acceptingChecker: ShipperExistenceChecker = (_: ShipperId) => true

  private def baseRouteSpec = RouteSpecification(
    Location.unsafeFrom("JPYOK"),
    Location.unsafeFrom("USNYC"),
    LocalDate.now.plusDays(30)
  ).toOption.get

  test("冷凍貨物の予約を save → findById で温度範囲が復元される"):
    val repo = new ScalikeJdbcCargoRepository
    val refrig = RefrigerationSpec(-20, -5, TemperatureUnit.Celsius).toOption.get
    val spec = CargoSpec(
      cargoType = CargoType.Refrigerated,
      weight = Weight(500).toOption.get,
      description = Some("冷凍マグロ"),
      quantity = Some(10),
      hazardous = None,
      refrigeration = Some(refrig)
    )
    val cargo = Cargo
      .book(
        BookingId.unsafeFrom("BK-RFG001"),
        ShipperId.unsafeFrom("SH-000001"),
        baseRouteSpec,
        spec,
        acceptingChecker
      )
      .toOption
      .get

    repo.save(cargo)
    val found = repo.findById(cargo.bookingId).get
    found.cargoSpec.cargoType shouldBe CargoType.Refrigerated
    found.cargoSpec.refrigeration shouldBe Some(refrig)
    found.cargoSpec.hazardous shouldBe None

  test("一般貨物の予約は refrigeration が None で復元される"):
    val repo = new ScalikeJdbcCargoRepository
    val spec = CargoSpec(
      cargoType = CargoType.General,
      weight = Weight(800).toOption.get,
      description = None,
      quantity = None,
      hazardous = None
    )
    val cargo = Cargo
      .book(
        BookingId.unsafeFrom("BK-GEN001"),
        ShipperId.unsafeFrom("SH-000001"),
        baseRouteSpec,
        spec,
        acceptingChecker
      )
      .toOption
      .get

    repo.save(cargo)
    val found = repo.findById(cargo.bookingId).get
    found.cargoSpec.refrigeration shouldBe None

  test("危険物 + 冷凍を併せ持つ予約も round-trip 可能"):
    val repo = new ScalikeJdbcCargoRepository
    val haz = HazardousDeclaration("3", "UN1170", "ETHANOL")
    val refrig = RefrigerationSpec(-30, -10, TemperatureUnit.Celsius).toOption.get
    val spec = CargoSpec(
      cargoType = CargoType.Hazardous,
      weight = Weight(200).toOption.get,
      description = Some("引火性液体"),
      quantity = Some(1),
      hazardous = Some(haz),
      refrigeration = Some(refrig)
    )
    val cargo = Cargo
      .book(
        BookingId.unsafeFrom("BK-HAZ001"),
        ShipperId.unsafeFrom("SH-000001"),
        baseRouteSpec,
        spec,
        acceptingChecker
      )
      .toOption
      .get

    repo.save(cargo)
    val found = repo.findById(cargo.bookingId).get
    found.cargoSpec.hazardous shouldBe Some(haz)
    found.cargoSpec.refrigeration shouldBe Some(refrig)
