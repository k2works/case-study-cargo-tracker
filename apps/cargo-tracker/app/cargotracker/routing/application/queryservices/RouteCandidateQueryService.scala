package cargotracker.routing.application.queryservices

import cargotracker.routing.application.RouteCandidateSearch
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.RouteCandidate
import cargotracker.shared.domain.{CargoType, Location}

import java.time.Instant
import javax.inject.{Inject, Singleton}

/** 経路候補算出コマンド（US08）。
  *
  *   - `origin` / `destination` / `earliestDeparture`: 必須
  *   - `cargoType`: 任意。指定時は対応航海のみで探索する
  *   - `maxLegs`: 探索深さ。デフォルト 3
  *   - `topN`: 結果に含める候補数。デフォルト 5
  */
final case class CalculateRouteCommand(
    origin: String,
    destination: String,
    earliestDeparture: Instant,
    cargoType: Option[String] = None,
    maxLegs: Int = 3,
    topN: Int = 5
)

/** US08 経路候補算出アプリケーションサービス。
  *
  *   - 全 `Voyage` を取得
  *   - 貨物種別フィルタを適用して `RoutingLeg` を生成
  *   - DFS で全列挙し、上位 N を返す
  *   - 0 件時は呼び出し側で条件緩和ガイドを表示する想定
  */
@Singleton
class RouteCandidateQueryService @Inject() (voyageRepository: VoyageRepository):

  def calculateCandidates(command: CalculateRouteCommand): Either[String, List[RouteCandidate]] =
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
      RouteCandidateSearch.topN(candidates, command.topN)

  private def liftCargoType(raw: Option[String]): Either[String, Option[CargoType]] =
    raw.filter(_.nonEmpty) match
      case None => Right(None)
      case Some(name) => CargoType.fromName(name).toRight(s"貨物種別が不正です: $name").map(Some.apply)
