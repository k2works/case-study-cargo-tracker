package cargotracker.routing.application

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, VoyageNumber}
import cargotracker.shared.domain.Location

import java.time.Instant

/** US08 経路候補算出スパイク（IT3 着手前の技術検証、IT2 タスク 4.x）。
  *
  * 純関数で実装する DFS + 深さ制限の経路探索:
  *   - グラフの頂点 = `Location`、辺 = `RoutingLeg`（航海 1 区間に相当）
  *   - 出発地から目的地まで辺を辿り、`maxLegs` 区間以内で到達するすべての経路を列挙する
  *   - 同一地点の再訪は禁止（サイクル除去）
  *   - 区間の連結条件: 前区間の到着時刻 ≤ 次区間の出発時刻、かつ前区間の到着地 == 次区間の出発地
  *
  * 本実装はあくまで IT3 US08 の参照プロトタイプ。本番では:
  *   - グラフ事前構築（隣接リスト化、O(V+E)）
  *   - 料金 / 所要日数 / 危険物対応の制約フィルタ
  *   - 上位 N 候補のスコアリング
  * を `RouteCandidate` として返す予定。
  */
object RouteCandidateSearchSpike:

  /** 経路探索の入力辺。 */
  final case class RoutingLeg(
      voyageNumber: VoyageNumber,
      from: Location,
      to: Location,
      departure: Instant,
      arrival: Instant
  )

  /** 探索結果の経路（辺の列）。 */
  final case class RouteCandidate(legs: List[RoutingLeg]):
    require(legs.nonEmpty, "経路は 1 区間以上必要")
    def origin: Location = legs.head.from
    def destination: Location = legs.last.to
    def departure: Instant = legs.head.departure
    def arrival: Instant = legs.last.arrival
    def transitDays: Long =
      java.time.Duration.between(departure, arrival).toDays
    def voyages: List[VoyageNumber] = legs.map(_.voyageNumber).distinct

  /** すべての登録済み `Voyage` から `RoutingLeg` のフラットリストを生成する。 */
  def toRoutingLegs(voyages: Seq[Voyage]): List[RoutingLeg] =
    voyages.toList.flatMap { v =>
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

  /** DFS + 深さ制限で `origin → destination` の経路を全列挙する。
    *
    * @param legs
    *   利用可能な辺一覧
    * @param origin
    *   出発地
    * @param destination
    *   目的地
    * @param earliestDeparture
    *   最早出発可能時刻（前段からの到着時刻に相当、無指定は `Instant.MIN`）
    * @param maxLegs
    *   最大区間数（深さ制限）。IT2 暫定値 3
    */
  def search(
      legs: List[RoutingLeg],
      origin: Location,
      destination: Location,
      earliestDeparture: Instant = Instant.MIN,
      maxLegs: Int = 3
  ): List[RouteCandidate] =
    def loop(
        current: Location,
        notBefore: Instant,
        visited: Set[Location],
        acc: List[RoutingLeg]
    ): List[List[RoutingLeg]] =
      if acc.size > maxLegs then Nil
      else if current == destination && acc.nonEmpty then List(acc.reverse)
      else
        legs
          .filter(l => l.from == current && !visited.contains(l.to) && !l.departure.isBefore(notBefore))
          .flatMap { next =>
            loop(next.to, next.arrival, visited + next.to, next :: acc)
          }

    loop(origin, earliestDeparture, Set(origin), Nil).map(RouteCandidate(_))
