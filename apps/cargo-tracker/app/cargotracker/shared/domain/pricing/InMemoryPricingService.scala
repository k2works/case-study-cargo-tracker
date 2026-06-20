package cargotracker.shared.domain.pricing

import cargotracker.shared.domain.{CargoType, Location, Money, Weight}

import javax.inject.Singleton

/** IT1 用のインメモリ固定単価実装。
  *
  *   - JPY 基本単価 = 100 円/kg
  *   - 距離係数: 出発地と目的地の UnLocode の文字差から擬似的に算出
  *   - 貨物種別係数: General=1.0, Refrigerated=1.3, Hazardous=1.6
  */
@Singleton
class InMemoryPricingService extends PricingService:

  private val BasePerKg = 100L

  override def estimateCost(
      origin: Location,
      destination: Location,
      cargoType: CargoType,
      weight: Weight,
      candidateVoyage: Option[String]
  ): Either[PricingService.Error, Money] =
    if origin.unLocode == destination.unLocode then Left(PricingService.SameOriginAndDestination)
    else
      val distanceFactor = math.max(
        1,
        math.abs(
          origin.unLocode.hashCode % 50 - destination.unLocode.hashCode % 50
        )
      )
      val typeFactor: Double = cargoType match
        case CargoType.General => 1.0
        case CargoType.Refrigerated => 1.3
        case CargoType.Hazardous => 1.6

      val base = BasePerKg * weight.kg
      val amount = (base * distanceFactor * typeFactor).toLong
      Money
        .jpy(amount)
        .left
        .map(_ => PricingService.PriceCalculationFailed)
