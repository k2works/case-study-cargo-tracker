package cargotracker.routing.application.queryservices

import java.time.Instant

/** 経路候補算出コマンド（US08）。
  *
  *   - `origin` / `destination` / `earliestDeparture`: 必須
  *   - `cargoType`: 任意。指定時は対応航海のみで探索する
  *   - `weightKg`: 任意。指定時は料金算出を行う
  *   - `maxLegs`: 探索深さ。デフォルト 3
  *   - `topN`: 結果に含める候補数。デフォルト 5
  */
final case class CalculateRouteCommand(
    origin: String,
    destination: String,
    earliestDeparture: Instant,
    cargoType: Option[String] = None,
    weightKg: Option[Long] = None,
    maxLegs: Int = 3,
    topN: Int = 5
)
