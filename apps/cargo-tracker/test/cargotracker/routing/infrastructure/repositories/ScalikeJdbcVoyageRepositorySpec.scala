package cargotracker.routing.infrastructure.repositories

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.{CargoType, Location}
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

  // === ADR 0006 拡張: 船名 / 運送会社 / 対応貨物種別と findByCriteria（IT3 タスク 1.6 / US07） ===

  test("save → findByVoyageNumber で vessel_name / carrier_code / supportedCargoTypes が復元される"):
    val repo = new ScalikeJdbcVoyageRepository
    val v = Voyage.register(
      voyageNumber,
      schedule1,
      vesselName = "MV ALPHA",
      carrierCode = "MOL",
      supportedCargoTypes = Set(CargoType.General, CargoType.Refrigerated)
    )
    repo.save(v)
    val found = repo.findByVoyageNumber(voyageNumber).get
    found.vesselName shouldBe "MV ALPHA"
    found.carrierCode shouldBe "MOL"
    found.supportedCargoTypes shouldBe Set(CargoType.General, CargoType.Refrigerated)

  test("findByCriteria: 全条件未指定なら findAll と同件数を返す"):
    val repo = new ScalikeJdbcVoyageRepository
    repo.save(Voyage.register(VoyageNumber.unsafeFrom("VY-A1"), schedule1))
    repo.save(Voyage.register(VoyageNumber.unsafeFrom("VY-A2"), schedule2))
    repo.findByCriteria().map(_.voyageNumber.value) should contain theSameElementsAs Seq(
      "VY-A1",
      "VY-A2"
    )

  test("findByCriteria: 出発港で絞り込みできる"):
    val repo = new ScalikeJdbcVoyageRepository
    repo.save(Voyage.register(VoyageNumber.unsafeFrom("VY-T1"), schedule1)) // 出発: JPTYO
    val schedFromYok = Schedule(
      List(
        CarrierMovement(
          yok,
          lax,
          Instant.parse("2026-07-05T10:00:00Z"),
          Instant.parse("2026-07-15T20:00:00Z")
        ).toOption.get
      )
    ).toOption.get
    repo.save(Voyage.register(VoyageNumber.unsafeFrom("VY-Y1"), schedFromYok))

    val results = repo.findByCriteria(origin = Some(tyo))
    results.map(_.voyageNumber.value) shouldBe Seq("VY-T1")

  test("findByCriteria: 貨物種別で絞り込みできる"):
    val repo = new ScalikeJdbcVoyageRepository
    repo.save(
      Voyage.register(
        VoyageNumber.unsafeFrom("VY-HZ"),
        schedule1,
        "VESSEL-HZ",
        "NYK",
        Set(CargoType.Hazardous)
      )
    )
    repo.save(
      Voyage.register(
        VoyageNumber.unsafeFrom("VY-GN"),
        schedule1,
        "VESSEL-GN",
        "MAERSK",
        Set(CargoType.General)
      )
    )
    val results = repo.findByCriteria(cargoType = Some(CargoType.Hazardous))
    results.map(_.voyageNumber.value) shouldBe Seq("VY-HZ")

  test("findByCriteria: 出港期間で絞り込みできる"):
    val repo = new ScalikeJdbcVoyageRepository
    repo.save(Voyage.register(VoyageNumber.unsafeFrom("VY-JUL"), schedule1)) // 2026-07-01
    repo.save(Voyage.register(VoyageNumber.unsafeFrom("VY-AUG"), schedule2)) // 2026-07-02 開始
    val results = repo.findByCriteria(
      departureFrom = Some(Instant.parse("2026-07-02T00:00:00Z")),
      departureTo = Some(Instant.parse("2026-07-02T23:59:59Z"))
    )
    results.map(_.voyageNumber.value) shouldBe Seq("VY-AUG")
