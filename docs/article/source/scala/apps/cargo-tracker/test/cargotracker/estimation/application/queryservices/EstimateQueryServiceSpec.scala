package cargotracker.estimation.application.queryservices

import cargotracker.estimation.domain.model.aggregates.Estimate
import cargotracker.estimation.domain.model.repositories.EstimateRepository
import cargotracker.estimation.domain.model.valueobjects.{EstimateId, RouteCandidate}
import cargotracker.shared.domain.{CargoType, Location, Money, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate
import scala.collection.mutable

class EstimateQueryServiceSpec extends AnyFunSuite with Matchers:

  private val origin = Location.unsafeFrom("JPTYO")
  private val destination = Location.unsafeFrom("USLAX")
  private val cost = Money("JPY", 100_000L).toOption.get
  private val candidate =
    RouteCandidate("VY-001", List(origin.unLocode, destination.unLocode), 14, cost)
  private val estimate = Estimate
    .create(
      origin,
      destination,
      LocalDate.now.plusDays(30),
      CargoType.General,
      Weight(1000).toOption.get,
      List(candidate)
    )
    .toOption
    .get

  private class InMemoryEstimateRepository(seed: Estimate*) extends EstimateRepository:
    private val store = mutable.Map.from(seed.map(e => e.estimateId -> e))
    override def findById(id: EstimateId): Option[Estimate] = store.get(id)
    override def findAll(): Seq[Estimate] = store.values.toSeq
    override def save(e: Estimate): Unit = store.update(e.estimateId, e)

  test("findAll はリポジトリの全件を返す"):
    val service = new EstimateQueryService(new InMemoryEstimateRepository(estimate))
    service.findAll() should contain only estimate

  test("findById は文字列 ID で見積を取得する"):
    val service = new EstimateQueryService(new InMemoryEstimateRepository(estimate))
    service.findById(estimate.estimateId.value) shouldBe Some(estimate)

  test("findById は未知の ID で None を返す"):
    val service = new EstimateQueryService(new InMemoryEstimateRepository(estimate))
    service.findById("ES-UNKNOWN") shouldBe None
