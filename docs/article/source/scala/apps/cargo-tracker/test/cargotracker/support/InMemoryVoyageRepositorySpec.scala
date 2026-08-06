package cargotracker.support

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.{CargoType, Location}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

/** [[InMemoryVoyageRepository.findByCriteria]] の契約テスト。
  *
  * ScalikeJDBC 版（`ScalikeJdbcVoyageRepositorySpec`）と同じ性質を Scala 側で再現していることを担保する。
  */
class InMemoryVoyageRepositorySpec extends AnyFunSuite with Matchers:

  private val tyo = Location.unsafeFrom("JPTYO")
  private val yok = Location.unsafeFrom("JPYOK")
  private val lax = Location.unsafeFrom("USLAX")

  private def voyage(
      vn: String,
      from: Location,
      to: Location,
      departure: Instant,
      supported: Set[CargoType] = Set(CargoType.General)
  ): Voyage =
    val sched = Schedule(
      List(
        CarrierMovement(from, to, departure, departure.plusSeconds(86400L)).toOption.get
      )
    ).toOption.get
    Voyage.register(VoyageNumber.unsafeFrom(vn), sched, "V", "C", supported)

  test("origin 指定時は最初の区間の出発地が一致する航海のみ返す"):
    val repo = new InMemoryVoyageRepository
    repo.save(voyage("VY-1", tyo, lax, Instant.parse("2026-07-01T00:00:00Z")))
    repo.save(voyage("VY-2", yok, lax, Instant.parse("2026-07-01T00:00:00Z")))

    repo.findByCriteria(origin = Some(tyo)).map(_.voyageNumber.value) shouldBe Seq("VY-1")

  test("destination 指定時は最後の区間の到着地が一致する航海のみ返す"):
    val repo = new InMemoryVoyageRepository
    repo.save(voyage("VY-1", tyo, lax, Instant.parse("2026-07-01T00:00:00Z")))
    repo.save(voyage("VY-2", tyo, yok, Instant.parse("2026-07-01T00:00:00Z")))

    repo.findByCriteria(destination = Some(lax)).map(_.voyageNumber.value) shouldBe Seq("VY-1")

  test("departureFrom / departureTo で出港時刻範囲を絞り込む"):
    val repo = new InMemoryVoyageRepository
    repo.save(voyage("VY-JUL", tyo, lax, Instant.parse("2026-07-15T00:00:00Z")))
    repo.save(voyage("VY-AUG", tyo, lax, Instant.parse("2026-08-15T00:00:00Z")))

    repo
      .findByCriteria(
        departureFrom = Some(Instant.parse("2026-07-01T00:00:00Z")),
        departureTo = Some(Instant.parse("2026-07-31T23:59:59Z"))
      )
      .map(_.voyageNumber.value) shouldBe Seq("VY-JUL")

  test("cargoType 指定時は対応している航海のみ返す"):
    val repo = new InMemoryVoyageRepository
    repo.save(voyage("VY-G", tyo, lax, Instant.parse("2026-07-01T00:00:00Z"), Set(CargoType.General)))
    repo.save(voyage("VY-H", tyo, lax, Instant.parse("2026-07-01T00:00:00Z"), Set(CargoType.Hazardous)))

    repo.findByCriteria(cargoType = Some(CargoType.Hazardous)).map(_.voyageNumber.value) shouldBe Seq("VY-H")

  test("複数条件は AND 結合で絞り込む"):
    val repo = new InMemoryVoyageRepository
    repo.save(voyage("VY-1", tyo, lax, Instant.parse("2026-07-15T00:00:00Z"), Set(CargoType.General)))
    repo.save(voyage("VY-2", tyo, lax, Instant.parse("2026-07-15T00:00:00Z"), Set(CargoType.Hazardous)))
    repo.save(voyage("VY-3", yok, lax, Instant.parse("2026-07-15T00:00:00Z"), Set(CargoType.General)))

    repo
      .findByCriteria(origin = Some(tyo), cargoType = Some(CargoType.General))
      .map(_.voyageNumber.value) shouldBe Seq("VY-1")
