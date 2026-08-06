package cargotracker.shared.domain.pricing

import cargotracker.shared.domain.{CargoType, Location, Money, Weight}

/** 料金計算ドメインサービス（ADR 0003）。
  *
  * Estimation Context（US01）と Billing Context（US21）の双方から呼び出される共有カーネル。
  *
  *   - IT1: モック実装（固定単価 + 貨物種別係数）
  *   - IT3 以降: `pricing_tariff` テーブルから単価を取得
  *   - IT6: 確定経路のレグ別単価を反映
  *   - IT7 0.9: `calculateActualWithBreakdown` で料金内訳（距離 / 重量 / 貨物種別）を返却
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

  /** 輸送実績ベースの実費を算出する（US21 / IT6）。 */
  def calculateActual(
      origin: Location,
      destination: Location,
      cargoType: CargoType,
      weight: Weight,
      itineraryVoyages: List[String]
  ): Either[PricingService.Error, Money] =
    estimateCost(origin, destination, cargoType, weight, itineraryVoyages.headOption)

  /** 実費 + 料金内訳を返す（IT7 0.9 / H6）。デフォルトは全額を `Other` カテゴリの単一明細にまとめる。 */
  def calculateActualWithBreakdown(
      origin: Location,
      destination: Location,
      cargoType: CargoType,
      weight: Weight,
      itineraryVoyages: List[String]
  ): Either[PricingService.Error, PricingService.Breakdown] =
    calculateActual(origin, destination, cargoType, weight, itineraryVoyages).map { total =>
      PricingService.Breakdown(
        total = total,
        items = List(PricingService.LineItem(PricingService.LineItemCategory.Other, "総額", total))
      )
    }

object PricingService:
  sealed trait Error
  case object SameOriginAndDestination extends Error
  case object PriceCalculationFailed extends Error

  /** PricingService 層の料金内訳 (Billing / Estimation から `InvoiceLineItem` に変換される)。 */
  final case class Breakdown(total: Money, items: List[LineItem])

  enum LineItemCategory:
    case Distance
    case Weight
    case CargoType
    case Other

  final case class LineItem(category: LineItemCategory, name: String, amount: Money)
