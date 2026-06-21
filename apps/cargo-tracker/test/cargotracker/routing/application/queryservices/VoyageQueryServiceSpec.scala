package cargotracker.routing.application.queryservices

import cargotracker.routing.application.queryservices.SearchVoyageCommand
import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.{CargoType, Location}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Instant, LocalDate}
import scala.collection.mutable

/** VoyageQueryService.search の単体テスト（IT3 タスク 1.6 / US07）。 */
class VoyageQueryServiceSpec extends AnyFunSuite with Matchers:

  private class InMemoryVoyageRepository extends VoyageRepository:
    val store: mutable.Buffer[Voyage] = mutable.Buffer.empty
    val criteriaCalls: mutable.Buffer[(Option[Location], Option[Location], Option[CargoType])] =
      mutable.Buffer.empty
    override def findByVoyageNumber(vn: VoyageNumber): Option[Voyage] =
      store.find(_.voyageNumber == vn)
    override def findAll(): Seq[Voyage] = store.toSeq
    override def save(v: Voyage): Unit = store += v
    override def findByCriteria(
        origin: Option[Location],
        destination: Option[Location],
        departureFrom: Option[Instant],
        departureTo: Option[Instant],
        cargoType: Option[CargoType]
    ): Seq[Voyage] =
      criteriaCalls += ((origin, destination, cargoType))
      store.toSeq

  private val schedule = Schedule(
    List(
      CarrierMovement(
        Location.unsafeFrom("JPTYO"),
        Location.unsafeFrom("USLAX"),
        Instant.parse("2026-08-01T10:00:00Z"),
        Instant.parse("2026-08-15T18:00:00Z")
      ).toOption.get
    )
  ).toOption.get

  private val sampleVoyage =
    Voyage.register(
      VoyageNumber.unsafeFrom("VY-001"),
      schedule,
      vesselName = "MV ALPHA",
      carrierCode = "MOL",
      supportedCargoTypes = Set(CargoType.General, CargoType.Refrigerated)
    )

  test("search: 全条件未指定なら findByCriteria に空 Option 群が渡される"):
    val repo = new InMemoryVoyageRepository
    repo.save(sampleVoyage)
    val service = new VoyageQueryService(repo)

    val Right(results) = service.search(SearchVoyageCommand()): @unchecked

    results should have size 1
    repo.criteriaCalls.head shouldBe ((None, None, None))

  test("search: 出発港 / 到着港 / 貨物種別を Location / CargoType に変換して渡す"):
    val repo = new InMemoryVoyageRepository
    val service = new VoyageQueryService(repo)

    service.search(
      SearchVoyageCommand(
        origin = Some("JPTYO"),
        destination = Some("USLAX"),
        cargoType = Some("Refrigerated")
      )
    )

    repo.criteriaCalls.head shouldBe (
      (
        Some(Location.unsafeFrom("JPTYO")),
        Some(Location.unsafeFrom("USLAX")),
        Some(CargoType.Refrigerated)
      )
    )

  test("search: 出発港 UnLocode 不正は Left を返し repository は呼ばれない"):
    val repo = new InMemoryVoyageRepository
    val service = new VoyageQueryService(repo)

    val Left(msg) = service.search(SearchVoyageCommand(origin = Some("xx"))): @unchecked

    msg should include("出発港")
    repo.criteriaCalls shouldBe empty

  test("search: 到着港 UnLocode 不正は Left"):
    val repo = new InMemoryVoyageRepository
    val service = new VoyageQueryService(repo)
    service.search(SearchVoyageCommand(destination = Some("xx"))).isLeft shouldBe true

  test("search: 貨物種別が未知の名称は Left"):
    val repo = new InMemoryVoyageRepository
    val service = new VoyageQueryService(repo)
    val Left(msg) = service.search(SearchVoyageCommand(cargoType = Some("Unknown"))): @unchecked
    msg should include("貨物種別が不正です")

  test("search: 日付範囲は LocalDate → Instant に展開される（同日指定で正常）"):
    val repo = new InMemoryVoyageRepository
    val service = new VoyageQueryService(repo)
    service
      .search(
        SearchVoyageCommand(
          departureDateFrom = Some(LocalDate.of(2026, 8, 1)),
          departureDateTo = Some(LocalDate.of(2026, 8, 1))
        )
      )
      .isRight shouldBe true

  test("search: 空文字列の origin は条件なしと同等"):
    val repo = new InMemoryVoyageRepository
    val service = new VoyageQueryService(repo)
    service.search(SearchVoyageCommand(origin = Some(""))).isRight shouldBe true
    repo.criteriaCalls.head._1 shouldBe None
