package cargotracker.shared.domain.pricing

import cargotracker.shared.domain.{CargoType, Location, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class InMemoryPricingServiceSpec extends AnyFunSuite with Matchers:

  private val service = new InMemoryPricingService
  private val origin = Location("JPYOK", "横浜").toOption.get
  private val destination = Location("USNYC", "New York").toOption.get
  private val weight = Weight(100).toOption.get

  test("一般貨物の見積料金は正の JPY を返す"):
    val res =
      service.estimateCost(origin, destination, CargoType.General, weight, None)
    res.isRight shouldBe true
    res.toOption.get.amount should be > 0L
    res.toOption.get.currency shouldBe "JPY"

  test("危険物の係数は一般貨物より高い"):
    val general = service
      .estimateCost(origin, destination, CargoType.General, weight, None)
      .toOption
      .get
    val hazardous = service
      .estimateCost(origin, destination, CargoType.Hazardous, weight, None)
      .toOption
      .get
    hazardous.amount should be > general.amount

  test("冷凍貨物は一般貨物と危険物の中間"):
    val general = service
      .estimateCost(origin, destination, CargoType.General, weight, None)
      .toOption
      .get
    val refrigerated = service
      .estimateCost(origin, destination, CargoType.Refrigerated, weight, None)
      .toOption
      .get
    val hazardous = service
      .estimateCost(origin, destination, CargoType.Hazardous, weight, None)
      .toOption
      .get
    refrigerated.amount should (be > general.amount and be < hazardous.amount)

  test("出発地と目的地が同じ場合は SameOriginAndDestination を返す"):
    service.estimateCost(
      origin,
      origin,
      CargoType.General,
      weight,
      None
    ) shouldBe Left(PricingService.SameOriginAndDestination)

  // IT7 0.10: calculateActual 失敗系テスト

  test("calculateActual: 出発地と目的地が同じルートは SameOriginAndDestination (estimateCost 経由)"):
    service.calculateActual(
      origin,
      origin,
      CargoType.General,
      weight,
      Nil
    ) shouldBe Left(PricingService.SameOriginAndDestination)

  test("calculateActual: itineraryVoyages の先頭が candidateVoyage として estimateCost に伝播される"):
    val captured = new java.util.concurrent.atomic.AtomicReference[Option[String]](None)
    val fake = new PricingService:
      override def estimateCost(
          o: Location,
          d: Location,
          c: CargoType,
          w: Weight,
          candidateVoyage: Option[String]
      ): Either[PricingService.Error, cargotracker.shared.domain.Money] =
        captured.set(candidateVoyage)
        cargotracker.shared.domain.Money.jpy(1000L).left.map(_ => PricingService.PriceCalculationFailed)
    fake.calculateActual(origin, destination, CargoType.General, weight, List("VY-1", "VY-2"))
    captured.get() shouldBe Some("VY-1")

  test("calculateActual: 単価計算サービスが PriceCalculationFailed を返したら呼出元にそのまま伝播される"):
    val alwaysFails = new PricingService:
      override def estimateCost(
          o: Location,
          d: Location,
          c: CargoType,
          w: Weight,
          candidateVoyage: Option[String]
      ): Either[PricingService.Error, cargotracker.shared.domain.Money] =
        Left(PricingService.PriceCalculationFailed)
    alwaysFails.calculateActual(
      origin,
      destination,
      CargoType.General,
      weight,
      Nil
    ) shouldBe Left(PricingService.PriceCalculationFailed)

  test("calculateActual: itineraryVoyages が空でも estimateCost に candidateVoyage=None を渡して通常算出する"):
    val res = service.calculateActual(origin, destination, CargoType.General, weight, Nil)
    res.isRight shouldBe true
    res.toOption.get.currency shouldBe "JPY"
    res.toOption.get.amount should be > 0L
