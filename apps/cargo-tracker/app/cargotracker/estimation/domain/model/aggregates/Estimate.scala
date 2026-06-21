package cargotracker.estimation.domain.model.aggregates

import cargotracker.estimation.domain.model.aggregates.EstimateStatus
import cargotracker.estimation.domain.model.valueobjects.{CargoSpec, EstimateId, RouteCandidate, RouteSpec}
import cargotracker.shared.domain.{CargoType, Location, Weight}

import java.time.LocalDate

/** 見積の状態。 */
enum EstimateStatus:
  case Created
  case Expired

object EstimateStatus:
  def fromName(name: String): Option[EstimateStatus] =
    values.find(_.toString.equalsIgnoreCase(name))

/** 輸送見積（Estimation Context 集約ルート）。
  *
  *   - `routeSpec`: 出発地・目的地・希望期限
  *   - `cargoSpec`: 貨物種別・重量
  *   - `routeCandidates`: 候補ルート集合（空なら「希望期限に間に合うルートなし」）
  */
final case class Estimate private (
    estimateId: EstimateId,
    routeSpec: RouteSpec,
    cargoSpec: CargoSpec,
    status: EstimateStatus,
    routeCandidates: List[RouteCandidate],
    version: Int
):
  def hasRoutes: Boolean = routeCandidates.nonEmpty

  // 旧 API 互換アクセサ（Repository / View 側の段階的移行用）
  def origin: Location = routeSpec.origin
  def destination: Location = routeSpec.destination
  def deadline: LocalDate = routeSpec.deadline
  def cargoType: CargoType = cargoSpec.cargoType
  def weight: Weight = cargoSpec.weight

object Estimate:

  sealed trait Error
  case object SameOriginAndDestination extends Error

  /** 新規見積を生成する。 */
  def create(
      origin: Location,
      destination: Location,
      deadline: LocalDate,
      cargoType: CargoType,
      weight: Weight,
      routeCandidates: List[RouteCandidate]
  ): Either[Error, Estimate] =
    if origin.unLocode == destination.unLocode then Left(SameOriginAndDestination)
    else
      Right(
        Estimate(
          estimateId = EstimateId.generate(),
          routeSpec = RouteSpec(origin, destination, deadline),
          cargoSpec = CargoSpec(cargoType, weight),
          status = EstimateStatus.Created,
          routeCandidates = routeCandidates,
          version = 0
        )
      )

  /** 永続化からの復元。 */
  def reconstruct(
      estimateId: EstimateId,
      routeSpec: RouteSpec,
      cargoSpec: CargoSpec,
      status: EstimateStatus,
      routeCandidates: List[RouteCandidate],
      version: Int = 0
  ): Estimate =
    Estimate(estimateId, routeSpec, cargoSpec, status, routeCandidates, version)
