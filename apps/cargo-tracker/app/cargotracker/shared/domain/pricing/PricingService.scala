package cargotracker.shared.domain.pricing

import cargotracker.shared.domain.{CargoType, Location, Money, Weight}

/** 料金計算ドメインサービス（ADR 0003）。
  *
  * Estimation Context（US01）と Billing Context（US21）の双方から呼び出される共有カーネル。
  *
  *   - IT1: モック実装（固定単価 + 貨物種別係数）
  *   - IT3 以降: `pricing_tariff` テーブルから単価を取得
  *   - IT6: 確定経路のレグ別単価を反映
  */
trait PricingService:

  /** 出発地・目的地・貨物種別・重量・候補航海から base price を算出する。 */
  def estimateCost(
      origin: Location,
      destination: Location,
      cargoType: CargoType,
      weight: Weight,
      candidateVoyage: Option[String]
  ): Either[PricingService.Error, Money]

object PricingService:
  sealed trait Error
  case object SameOriginAndDestination extends Error
  case object PriceCalculationFailed extends Error
