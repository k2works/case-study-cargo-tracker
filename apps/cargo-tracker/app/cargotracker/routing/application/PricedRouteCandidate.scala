package cargotracker.routing.application

import cargotracker.routing.domain.model.valueobjects.RouteCandidate
import cargotracker.shared.domain.Money

/** 経路候補に料金見積もりを付与した値オブジェクト（IT3 タスク 2.3 / US08）。
  *
  * `estimatedCost` は `weightKg` 指定時に `PricingService` で算出した合計金額。 料金計算に失敗した場合は `None`（経路自体は表示するが料金未算出を意味する）。
  */
final case class PricedRouteCandidate(candidate: RouteCandidate, estimatedCost: Option[Money])
