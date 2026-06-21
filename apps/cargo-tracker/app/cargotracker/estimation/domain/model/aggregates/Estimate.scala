package cargotracker.estimation.domain.model.aggregates

import cargotracker.estimation.domain.model.aggregates.EstimateStatus
import cargotracker.estimation.domain.model.valueobjects.{EstimateId, RouteCandidate}
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
  *   - 出発地・目的地・希望期限・貨物種別・重量を保持
  *   - 候補ルート（`RouteCandidate`）の集合を持つ
  *   - 候補ルートが空の場合は「希望期限に間に合うルートなし」と解釈する
  */
final case class Estimate private (
    estimateId: EstimateId,
    origin: Location,
    destination: Location,
    deadline: LocalDate,
    cargoType: CargoType,
    weight: Weight,
    status: EstimateStatus,
    routeCandidates: List[RouteCandidate]
):
  /** 候補ルートがあるか。 */
  def hasRoutes: Boolean = routeCandidates.nonEmpty

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
          origin = origin,
          destination = destination,
          deadline = deadline,
          cargoType = cargoType,
          weight = weight,
          status = EstimateStatus.Created,
          routeCandidates = routeCandidates
        )
      )

  /** 永続化からの復元 */
  def reconstruct(
      estimateId: EstimateId,
      origin: Location,
      destination: Location,
      deadline: LocalDate,
      cargoType: CargoType,
      weight: Weight,
      status: EstimateStatus,
      routeCandidates: List[RouteCandidate]
  ): Estimate =
    Estimate(
      estimateId = estimateId,
      origin = origin,
      destination = destination,
      deadline = deadline,
      cargoType = cargoType,
      weight = weight,
      status = status,
      routeCandidates = routeCandidates
    )
