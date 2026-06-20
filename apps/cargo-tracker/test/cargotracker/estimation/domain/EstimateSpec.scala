package cargotracker.estimation.domain

import cargotracker.shared.domain.{CargoType, Location, Money, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate

class EstimateSpec extends AnyFunSuite with Matchers:

  private val yok = Location("JPYOK").toOption.get
  private val nyc = Location("USNYC").toOption.get
  private val w = Weight(100).toOption.get
  private val candidate = RouteCandidate(
    voyageNumber = "VY-001",
    transitPorts = List("JPYOK", "USNYC"),
    transitDays = 10,
    estimatedCost = Money.jpy(150_000).toOption.get
  )

  test("有効な入力から見積を生成し EstimateId が発行される"):
    val res = Estimate.create(
      yok,
      nyc,
      LocalDate.now.plusDays(30),
      CargoType.General,
      w,
      List(candidate)
    )
    res.isRight shouldBe true
    val est = res.toOption.get
    est.estimateId.value should not be empty
    est.status shouldBe EstimateStatus.Created
    est.hasRoutes shouldBe true

  test("出発地と目的地が同じ見積は SameOriginAndDestination を返す"):
    Estimate.create(
      yok,
      yok,
      LocalDate.now,
      CargoType.General,
      w,
      Nil
    ) shouldBe Left(Estimate.SameOriginAndDestination)

  test("候補ルートが空の見積は hasRoutes が false"):
    val res = Estimate
      .create(yok, nyc, LocalDate.now.plusDays(7), CargoType.General, w, Nil)
      .toOption
      .get
    res.hasRoutes shouldBe false

  test("RouteCandidate の transitChain は経由港を→で結合する"):
    candidate.transitChain shouldBe "JPYOK→USNYC"
