package cargotracker.routing.application.queryservices

import cargotracker.routing.application.RouteCandidateSearch
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.RouteCandidate
import cargotracker.shared.domain.pricing.PricingService
import cargotracker.shared.domain.{CargoType, Location, Money, Weight}

import java.time.Instant
import javax.inject.{Inject, Singleton}

/** 経路候補算出コマンド（US08）。
  *
  *   - `origin` / `destination` / `earliestDeparture`: 必須
  *   - `cargoType`: 任意。指定時は対応航海のみで探索する
  *   - `weightKg`: 任意。指定時は料金算出を行う（IT3 タスク 2.3）
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

/** 経路候補に料金見積もりを付与した値オブジェクト（IT3 タスク 2.3）。
  *
  * `estimatedCost` は `weightKg` 指定時に `PricingService` で算出した合計金額。 料金計算に失敗した場合は `None`（経路自体は表示するが料金未算出を意味する）。
  */
final case class PricedRouteCandidate(candidate: RouteCandidate, estimatedCost: Option[Money])

/** US08 経路候補算出アプリケーションサービス。
  *
  *   - 全 `Voyage` を取得
  *   - 貨物種別フィルタを適用して `RoutingLeg` を生成
  *   - DFS で全列挙し、上位 N を返す
  *   - `weightKg` 指定時は `PricingService` で各候補の合計料金を付与
  */
@Singleton
class RouteCandidateQueryService @Inject() (
    voyageRepository: VoyageRepository,
    pricingService: PricingService
):

  def calculateCandidates(
      command: CalculateRouteCommand
  ): Either[String, List[PricedRouteCandidate]] =
    for
      origin <- Location(command.origin).left.map(_ => "出発港の UnLocode 形式が不正です")
      destination <- Location(command.destination).left.map(_ => "目的地の UnLocode 形式が不正です")
      cargoType <- liftCargoType(command.cargoType)
    yield
      val voyages = voyageRepository.findAll()
      val legs = RouteCandidateSearch.toRoutingLegs(voyages, cargoType)
      val candidates = RouteCandidateSearch.search(
        legs,
        origin,
        destination,
        command.earliestDeparture,
        command.maxLegs
      )
      val top = RouteCandidateSearch.topN(candidates, command.topN)
      top.map(c => PricedRouteCandidate(c, price(c, cargoType, command.weightKg)))

  private def price(
      candidate: RouteCandidate,
      cargoType: Option[CargoType],
      weightKg: Option[Long]
  ): Option[Money] =
    for
      ct <- cargoType
      w <- weightKg.flatMap(kg => Weight(kg).toOption)
      total <- candidate.legs
        .map(leg => pricingService.estimateCost(leg.from, leg.to, ct, w, Some(leg.voyageNumber.value)).toOption)
        .foldLeft[Option[Money]](Some(Money.zeroJpy)) { (accOpt, costOpt) =>
          for a <- accOpt; c <- costOpt; sum <- (a + c).toOption yield sum
        }
    yield total

  private def liftCargoType(raw: Option[String]): Either[String, Option[CargoType]] =
    raw.filter(_.nonEmpty) match
      case None => Right(None)
      case Some(name) => CargoType.fromName(name).toRight(s"貨物種別が不正です: $name").map(Some.apply)
