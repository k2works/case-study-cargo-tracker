package cargotracker.routing.application.queryservices

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.pricing.PricingService
import cargotracker.shared.domain.{CargoType, Location, Money, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.collection.mutable

/** RouteCandidateQueryService.calculateCandidates の単体テスト（IT3 タスク 2.7 / US08）。 */
class RouteCandidateQueryServiceSpec extends AnyFunSuite with Matchers:

  private object StubPricingService extends PricingService:
    override def estimateCost(
        origin: Location,
        destination: Location,
        cargoType: CargoType,
        weight: Weight,
        candidateVoyage: Option[String]
    ): Either[PricingService.Error, Money] = Money.jpy(1000L).left.map(_ => PricingService.PriceCalculationFailed)

  private val tyo = Location.unsafeFrom("JPTYO")
  private val yok = Location.unsafeFrom("JPYOK")
  private val lax = Location.unsafeFrom("USLAX")

  private class InMemoryVoyageRepository extends VoyageRepository:
    val store: mutable.Buffer[Voyage] = mutable.Buffer.empty
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
    ): Seq[Voyage] = store.toSeq

  private def directVoyage(
      vn: String,
      from: Location,
      to: Location,
      supported: Set[CargoType] = Set(CargoType.General)
  ): Voyage =
    val sched = Schedule(
      List(
        CarrierMovement(
          from,
          to,
          Instant.parse("2026-07-01T10:00:00Z"),
          Instant.parse("2026-07-10T18:00:00Z")
        ).toOption.get
      )
    ).toOption.get
    Voyage.register(VoyageNumber.unsafeFrom(vn), sched, "V", "C", supported)

  test("calculateCandidates: 直行便があれば 1 候補を返す"):
    val repo = new InMemoryVoyageRepository
    repo.save(directVoyage("VY-1", tyo, lax))
    val service = new RouteCandidateQueryService(repo, StubPricingService)

    val Right(candidates) = service.calculateCandidates(
      CalculateRouteCommand(
        origin = "JPTYO",
        destination = "USLAX",
        earliestDeparture = Instant.parse("2026-07-01T00:00:00Z")
      )
    ): @unchecked

    candidates.size shouldBe 1
    candidates.head.candidate.origin shouldBe tyo

  test("calculateCandidates: 出発港の UnLocode 不正は Left"):
    val repo = new InMemoryVoyageRepository
    val service = new RouteCandidateQueryService(repo, StubPricingService)
    val Left(msg) =
      service.calculateCandidates(
        CalculateRouteCommand("xx", "USLAX", Instant.parse("2026-07-01T00:00:00Z"))
      ): @unchecked
    msg should include("出発港")

  test("calculateCandidates: 貨物種別フィルタで対応航海のみ候補化する"):
    val repo = new InMemoryVoyageRepository
    repo.save(directVoyage("VY-G", tyo, lax, Set(CargoType.General)))
    repo.save(directVoyage("VY-H", tyo, lax, Set(CargoType.Hazardous)))
    val service = new RouteCandidateQueryService(repo, StubPricingService)

    val Right(candidates) = service.calculateCandidates(
      CalculateRouteCommand(
        origin = "JPTYO",
        destination = "USLAX",
        earliestDeparture = Instant.parse("2026-07-01T00:00:00Z"),
        cargoType = Some("Hazardous")
      )
    ): @unchecked
    candidates.flatMap(_.candidate.voyages).map(_.value) shouldBe List("VY-H")

  test("calculateCandidates: 該当航海なしなら空 List（条件緩和ガイドは UI 側）"):
    val repo = new InMemoryVoyageRepository
    val service = new RouteCandidateQueryService(repo, StubPricingService)
    val Right(candidates) = service.calculateCandidates(
      CalculateRouteCommand("JPTYO", "USLAX", Instant.parse("2026-07-01T00:00:00Z"))
    ): @unchecked
    candidates shouldBe empty

  test("calculateCandidates: topN 指定数で打ち切る"):
    val repo = new InMemoryVoyageRepository
    repo.save(directVoyage("VY-1", tyo, lax))
    repo.save(directVoyage("VY-2", tyo, yok))
    repo.save(directVoyage("VY-3", yok, lax))
    val service = new RouteCandidateQueryService(repo, StubPricingService)
    val Right(candidates) = service.calculateCandidates(
      CalculateRouteCommand(
        "JPTYO",
        "USLAX",
        Instant.parse("2026-06-30T00:00:00Z"),
        topN = 1
      )
    ): @unchecked
    candidates.size shouldBe 1
