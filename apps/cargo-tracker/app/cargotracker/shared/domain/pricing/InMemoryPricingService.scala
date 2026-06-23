package cargotracker.shared.domain.pricing

import cargotracker.shared.domain.{CargoType, Location, Money, Weight}

import javax.inject.Singleton

/** IT1 用のインメモリ固定単価実装。
  *
  *   - JPY 基本単価 = 100 円/kg
  *   - 距離係数: 出発地と目的地の UnLocode の文字差から擬似的に算出
  *   - 貨物種別係数: General=1.0, Refrigerated=1.3, Hazardous=1.6
  *   - IT7 0.9: `calculateActualWithBreakdown` で 3 明細 (距離 / 重量 / 貨物種別) を返却
  */
@Singleton
class InMemoryPricingService extends PricingService:

  private val BasePerKg = 100L

  private def factors(
      origin: Location,
      destination: Location,
      cargoType: CargoType
  ): (Long, Double) =
    val distanceFactor: Long = math.max(
      1L,
      math.abs(origin.unLocode.hashCode % 50 - destination.unLocode.hashCode % 50).toLong
    )
    val typeFactor: Double = cargoType match
      case CargoType.General => 1.0
      case CargoType.Refrigerated => 1.3
      case CargoType.Hazardous => 1.6
    (distanceFactor, typeFactor)

  override def estimateCost(
      origin: Location,
      destination: Location,
      cargoType: CargoType,
      weight: Weight,
      candidateVoyage: Option[String]
  ): Either[PricingService.Error, Money] =
    if origin.unLocode == destination.unLocode then Left(PricingService.SameOriginAndDestination)
    else
      val (distanceFactor, typeFactor) = factors(origin, destination, cargoType)
      val base = BasePerKg * weight.kg
      val amount = (base * distanceFactor * typeFactor).toLong
      Money
        .jpy(amount)
        .left
        .map(_ => PricingService.PriceCalculationFailed)

  override def calculateActualWithBreakdown(
      origin: Location,
      destination: Location,
      cargoType: CargoType,
      weight: Weight,
      itineraryVoyages: List[String]
  ): Either[PricingService.Error, PricingService.Breakdown] =
    if origin.unLocode == destination.unLocode then Left(PricingService.SameOriginAndDestination)
    else
      val (distanceFactor, typeFactor) = factors(origin, destination, cargoType)
      val baseWeightAmount = BasePerKg * weight.kg
      val distancePortion = baseWeightAmount * (distanceFactor - 1) // base 部分は重量料金に含む
      val typeMultiplierExtra = (baseWeightAmount * distanceFactor * (typeFactor - 1.0)).toLong
      val total = (baseWeightAmount * distanceFactor * typeFactor).toLong
      for
        weightMoney <- Money.jpy(baseWeightAmount).left.map(_ => PricingService.PriceCalculationFailed)
        distanceMoney <- Money.jpy(distancePortion).left.map(_ => PricingService.PriceCalculationFailed)
        typeMoney <- Money.jpy(typeMultiplierExtra).left.map(_ => PricingService.PriceCalculationFailed)
        totalMoney <- Money.jpy(total).left.map(_ => PricingService.PriceCalculationFailed)
      yield PricingService.Breakdown(
        total = totalMoney,
        items = List(
          PricingService.LineItem(PricingService.LineItemCategory.Weight, "重量料金", weightMoney),
          PricingService.LineItem(PricingService.LineItemCategory.Distance, "距離料金加算", distanceMoney),
          PricingService.LineItem(PricingService.LineItemCategory.CargoType, "貨物種別加算", typeMoney)
        )
      )
