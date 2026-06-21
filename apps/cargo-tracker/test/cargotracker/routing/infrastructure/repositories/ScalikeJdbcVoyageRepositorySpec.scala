package cargotracker.routing.infrastructure.repositories

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.Location
import cargotracker.support.{DbCleanupSupport, PostgresContainerSupport}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class ScalikeJdbcVoyageRepositorySpec
    extends AnyFunSuite
    with Matchers
    with PostgresContainerSupport
    with DbCleanupSupport:

  private val voyageNumber = VoyageNumber.unsafeFrom("VY-001")
  private val tyo = Location.unsafeFrom("JPTYO")
  private val yok = Location.unsafeFrom("JPYOK")
  private val lax = Location.unsafeFrom("USLAX")

  private def schedule1 = Schedule(
    List(
      CarrierMovement(
        tyo,
        yok,
        Instant.parse("2026-07-01T10:00:00Z"),
        Instant.parse("2026-07-01T18:00:00Z")
      ).toOption.get
    )
  ).toOption.get

  private def schedule2 = Schedule(
    List(
      CarrierMovement(
        tyo,
        yok,
        Instant.parse("2026-07-02T10:00:00Z"),
        Instant.parse("2026-07-02T18:00:00Z")
      ).toOption.get,
      CarrierMovement(
        yok,
        lax,
        Instant.parse("2026-07-03T08:00:00Z"),
        Instant.parse("2026-07-10T20:00:00Z")
      ).toOption.get
    )
  ).toOption.get

  test("save → findByVoyageNumber で航海と単一区間が復元される"):
    val repo = new ScalikeJdbcVoyageRepository
    val v = Voyage.register(voyageNumber, schedule1)
    repo.save(v)
    val found = repo.findByVoyageNumber(voyageNumber).get
    found.voyageNumber shouldBe voyageNumber
    found.schedule.carrierMovements.size shouldBe 1
    found.schedule.origin shouldBe tyo
    found.schedule.destination shouldBe yok
    found.version shouldBe 0

  test("save → 同 VoyageNumber で再 save（UPDATE）→ version+1、carrier_movement 入れ替え"):
    val repo = new ScalikeJdbcVoyageRepository
    repo.save(Voyage.register(voyageNumber, schedule1))
    repo.save(Voyage.register(voyageNumber, schedule2))

    val found = repo.findByVoyageNumber(voyageNumber).get
    found.version shouldBe 1
    found.schedule.carrierMovements.size shouldBe 2
    found.schedule.destination shouldBe lax

  test("findAll は登録順で voyage_number 昇順に取得する"):
    val repo = new ScalikeJdbcVoyageRepository
    repo.save(Voyage.register(VoyageNumber.unsafeFrom("VY-002"), schedule1))
    repo.save(Voyage.register(VoyageNumber.unsafeFrom("VY-001"), schedule1))
    repo.findAll().map(_.voyageNumber.value) shouldBe Seq("VY-001", "VY-002")

  test("未登録 VoyageNumber は None"):
    val repo = new ScalikeJdbcVoyageRepository
    repo.findByVoyageNumber(VoyageNumber.unsafeFrom("VY-999")) shouldBe None
