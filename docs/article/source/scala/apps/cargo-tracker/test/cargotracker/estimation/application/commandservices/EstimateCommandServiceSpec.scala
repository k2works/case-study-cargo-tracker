package cargotracker.estimation.application.commandservices

import cargotracker.estimation.domain.model.aggregates.Estimate
import cargotracker.estimation.domain.model.repositories.EstimateRepository
import cargotracker.estimation.domain.model.valueobjects.EstimateId
import cargotracker.shared.domain.pricing.PricingService
import cargotracker.shared.domain.{CargoType, Location, Money, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate
import scala.collection.mutable

class EstimateCommandServiceSpec extends AnyFunSuite with Matchers:

  private class InMemoryEstimateRepository extends EstimateRepository:
    private val store: mutable.Map[EstimateId, Estimate] = mutable.Map.empty
    override def findById(id: EstimateId): Option[Estimate] = store.get(id)
    override def findAll(): Seq[Estimate] = store.values.toSeq
    override def save(e: Estimate): Unit = store.update(e.estimateId, e)
    def saved: Seq[Estimate] = store.values.toSeq

  private val constantPrice = Money("JPY", 150_000L).toOption.get

  private val fixedPricing: PricingService = new PricingService:
    override def estimateCost(
        origin: Location,
        destination: Location,
        cargoType: CargoType,
        weight: Weight,
        candidateVoyage: Option[String]
    ): Either[PricingService.Error, Money] =
      if origin == destination then Left(PricingService.SameOriginAndDestination)
      else Right(constantPrice)

  private val failingPricing: PricingService =
    (_, _, _, _, _) => Left(PricingService.PriceCalculationFailed)

  private def validCommand: CreateEstimateCommand = CreateEstimateCommand(
    origin = "JPTYO",
    destination = "USLAX",
    deadline = LocalDate.now.plusDays(45),
    cargoType = "General",
    weightKg = 1500L
  )

  test("有効な入力で見積を生成し保存する"):
    val repo = new InMemoryEstimateRepository
    val service = new EstimateCommandService(repo, fixedPricing)

    val Right(estimate) = service.create(validCommand): @unchecked

    estimate.routeCandidates should have size 1
    estimate.routeCandidates.head.estimatedCost shouldBe constantPrice
    repo.saved should have size 1

  test("不正な UnLocode で出発地エラー"):
    val service = new EstimateCommandService(new InMemoryEstimateRepository, fixedPricing)
    service.create(validCommand.copy(origin = "xx")) shouldBe Left("出発地の UnLocode 形式が不正です")

  test("不正な貨物種別でエラー"):
    val service = new EstimateCommandService(new InMemoryEstimateRepository, fixedPricing)
    service.create(validCommand.copy(cargoType = "Unknown")) shouldBe Left("貨物種別が不正です")

  test("PricingService 失敗時はエラーメッセージにマップされ永続化されない"):
    val repo = new InMemoryEstimateRepository
    val service = new EstimateCommandService(repo, failingPricing)

    val Left(msg) = service.create(validCommand): @unchecked
    msg should include("料金算出に失敗しました")
    repo.saved shouldBe empty

  test("出発地 == 目的地で PricingService が SameOriginAndDestination を返しエラー文言にマップされる"):
    val service = new EstimateCommandService(new InMemoryEstimateRepository, fixedPricing)
    val Left(msg) =
      service.create(validCommand.copy(destination = validCommand.origin)): @unchecked
    msg should include("料金算出に失敗しました")
