package cargotracker.routing.application.commandservices

import cargotracker.routing.domain.model.aggregates.RouteCandidateSelection
import cargotracker.routing.domain.model.repositories.RouteCandidateSelectionRepository
import cargotracker.routing.domain.model.valueobjects.VoyageNumber

import javax.inject.{Inject, Singleton}

/** 経路選択ドメインのコマンドサービス（US09）。
  *
  *   - `confirmRoute`: 候補画面で選択された経路を `RouteCandidateSelection` として 保存し、即座に `Confirmed` 状態に遷移させる（PRG）。
  *   - 同一予約への再確定（既に Confirmed）は `AlreadyConfirmed` を返す。
  *   - 後続: US11 で `Cargo.assignItinerary` を呼び出し、Booking Context 側に紐付ける。
  */
@Singleton
class RoutingCommandService @Inject() (repository: RouteCandidateSelectionRepository):

  def confirmRoute(command: SelectRouteCommand): Either[String, RouteCandidateSelection] =
    for
      voyages <- parseVoyages(command.voyageNumbers)
      saved <- persistConfirmed(command.bookingId, voyages)
    yield saved

  private def parseVoyages(raw: List[String]): Either[String, List[VoyageNumber]] =
    if raw.isEmpty then Left("経路に含む航海が指定されていません")
    else traverseEither(raw)(s => VoyageNumber(s).left.map(_ => s"航海番号の形式が不正です: $s"))

  /** `List[A]` を `A => Either[E, B]` で写像し、最初の失敗で短絡する traverse 相当のヘルパ。 prepend + reverse で O(n) を維持する純粋実装。
    */
  private def traverseEither[A, E, B](
      xs: List[A]
  )(f: A => Either[E, B]): Either[E, List[B]] =
    xs
      .foldLeft[Either[E, List[B]]](Right(Nil)) { (acc, a) =>
        acc.flatMap(rs => f(a).map(_ :: rs))
      }
      .map(_.reverse)

  private def persistConfirmed(
      bookingId: String,
      voyages: List[VoyageNumber]
  ): Either[String, RouteCandidateSelection] =
    val base: Either[String, RouteCandidateSelection] = repository.findByBookingId(bookingId) match
      case Some(existing) => Right(existing)
      case None =>
        RouteCandidateSelection.create(bookingId, voyages).left.map {
          case RouteCandidateSelection.EmptyVoyages => "経路に含む航海が指定されていません"
          case _ => "経路の作成に失敗しました"
        }
    for
      selection <- base
      confirmed <- selection.confirm.left.map {
        case RouteCandidateSelection.AlreadyConfirmed => "この予約の経路は既に確定済みです"
        case _ => "経路の確定に失敗しました"
      }
    yield
      repository.save(confirmed)
      confirmed

/** 経路選択コマンド（US09）。
  *
  *   - `bookingId`: 予約番号
  *   - `voyageNumbers`: 選択された経路を構成する航海番号の順序リスト
  */
final case class SelectRouteCommand(bookingId: String, voyageNumbers: List[String])
