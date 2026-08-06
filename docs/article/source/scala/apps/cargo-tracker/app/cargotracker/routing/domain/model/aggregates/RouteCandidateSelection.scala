package cargotracker.routing.domain.model.aggregates

import cargotracker.routing.domain.model.valueobjects.{RouteSelectionStatus, VoyageNumber}

/** 経路選択（US09）の集約ルート。1 予約 1 選択（業務キー: BookingId）。
  *
  * ADR 0009: RouteCandidate（経路探索結果の値オブジェクト）と区別し、永続化対象として 「予約 → 選択された航海列 + 確定状態」を保持する集約として独立させる。
  *
  *   - `bookingId`: 業務キー（Booking Context への ID 参照、ACL は applicationservice 側で吸収）
  *   - `voyages`: 経路を構成する航海番号の順序リスト（1 つ以上）
  *   - `status`: `Pending` / `Confirmed`
  *   - `version`: 楽観ロック用
  */
final case class RouteCandidateSelection private (
    bookingId: String,
    voyages: List[VoyageNumber],
    status: RouteSelectionStatus,
    version: Int
):

  /** 経路選択を確定する。 */
  def confirm: Either[RouteCandidateSelection.Error, RouteCandidateSelection] = status match
    case RouteSelectionStatus.Pending =>
      Right(copy(status = RouteSelectionStatus.Confirmed))
    case RouteSelectionStatus.Confirmed =>
      Left(RouteCandidateSelection.AlreadyConfirmed)

object RouteCandidateSelection:

  sealed trait Error
  case object EmptyVoyages extends Error
  case object AlreadyConfirmed extends Error

  /** 新規作成（初期状態は Pending、version = 0）。 */
  def create(bookingId: String, voyages: List[VoyageNumber]): Either[Error, RouteCandidateSelection] =
    if voyages.isEmpty then Left(EmptyVoyages)
    else Right(new RouteCandidateSelection(bookingId, voyages, RouteSelectionStatus.Pending, version = 0))

  /** 永続化からの再構成。 */
  def reconstruct(
      bookingId: String,
      voyages: List[VoyageNumber],
      status: RouteSelectionStatus,
      version: Int
  ): RouteCandidateSelection =
    new RouteCandidateSelection(bookingId, voyages, status, version)
