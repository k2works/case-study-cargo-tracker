package cargotracker.routing.application

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.valueobjects.{RouteCandidate, RoutingLeg}
import cargotracker.shared.domain.{CargoType, Location}

import java.time.Instant

/** US08 経路候補算出（ADR 0005 採用 DFS + 深さ制限、IT2 spike から application 層に格上げ）。
  *
  *   - 純関数で実装（`Voyage`・`Location` のみに依存）
  *   - グラフの頂点 = `Location`、辺 = `RoutingLeg`
  *   - サイクル禁止（同一地点の再訪なし）
  *   - 区間連結条件: 前区間到着時刻 ≤ 次区間出発時刻、前到着地 == 次出発地
  */
object RouteCandidateSearch:

  /** すべての登録済み `Voyage` から `RoutingLeg` のフラットリストを生成する。
    *
    * `cargoTypeFilter` を指定した場合、その貨物種別に対応する航海のみを採用する（US05 受入条件 4 / US08 貨物種別フィルタ）。
    */
  def toRoutingLegs(
      voyages: Seq[Voyage],
      cargoTypeFilter: Option[CargoType] = None
  ): List[RoutingLeg] =
    voyages.toList
      .filter(v => cargoTypeFilter.forall(v.supports))
      .flatMap { v =>
        v.schedule.carrierMovements.map { cm =>
          RoutingLeg(
            voyageNumber = v.voyageNumber,
            from = cm.departureLocation,
            to = cm.arrivalLocation,
            departure = cm.departureTime,
            arrival = cm.arrivalTime
          )
        }
      }

  /** DFS + 深さ制限で `origin → destination` の経路を全列挙する。 */
  def search(
      legs: List[RoutingLeg],
      origin: Location,
      destination: Location,
      earliestDeparture: Instant = Instant.MIN,
      maxLegs: Int = 3
  ): List[RouteCandidate] =
    // 隣接リスト化で from ごとに辺を引けるよう前計算（IT3 改善・ADR 0005 申し送り）
    val byFrom: Map[Location, List[RoutingLeg]] = legs.groupBy(_.from)

    def loop(
        current: Location,
        notBefore: Instant,
        visited: Set[Location],
        acc: List[RoutingLeg]
    ): List[List[RoutingLeg]] =
      if acc.size > maxLegs then Nil
      else if current == destination && acc.nonEmpty then List(acc.reverse)
      else
        byFrom
          .getOrElse(current, Nil)
          .filter(l => !visited.contains(l.to) && !l.departure.isBefore(notBefore))
          .flatMap { next =>
            loop(next.to, next.arrival, visited + next.to, next :: acc)
          }

    loop(origin, earliestDeparture, Set(origin), Nil).map(RouteCandidate(_))
