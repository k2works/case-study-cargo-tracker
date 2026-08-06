package cargotracker.booking.domain.model.valueobjects

import cargotracker.shared.domain.Location

/** 予約に紐付けられた経路（US09 → US11、IT7 0.14 で leg 詳細を保持）。
  *
  * Booking Context の値オブジェクト。Routing Context の `RouteCandidateSelection` 集約から applicationservice 層で
  * ACL（変換アダプター）を介して受け取る。
  *
  *   - `voyageNumbers`: 経路を構成する航海番号の順序リスト（1 つ以上）。後方互換のため維持
  *   - `legs`: 区間別の `(voyageNumber, from, to)` 情報。永続化スキーマ V19 から復元される
  */
final case class Itinerary private (voyageNumbers: List[String], legs: List[ItineraryLeg]):
  require(voyageNumbers.nonEmpty, "経路は 1 航海以上必要")

  /** 経路上に存在する全 UN/LOCODE を返す（routeDeviation 判定用、IT7 0.14）。 */
  def expectedLocations: Set[String] =
    legs.flatMap(l => Seq(l.from.unLocode, l.to.unLocode)).toSet

  /** 指定 UN/LOCODE が経路上の予定地かどうかを判定する。 leg 詳細が未設定の場合は false（経路逸脱判定をスキップしない）。 */
  def isOnRoute(unLocode: String): Boolean =
    legs.nonEmpty && expectedLocations.contains(unLocode)

object Itinerary:
  sealed trait Error
  case object EmptyVoyages extends Error

  /** 航海番号のみから組み立てる（後方互換）。`legs` は空となり経路逸脱判定はスキップされる。 */
  def apply(voyageNumbers: List[String]): Either[Error, Itinerary] =
    if voyageNumbers.isEmpty then Left(EmptyVoyages)
    else Right(new Itinerary(voyageNumbers, Nil))

  /** 区間別 leg 情報から組み立てる（IT7 0.14、V19 から復元される正式形式）。 */
  def fromLegs(legs: List[ItineraryLeg]): Either[Error, Itinerary] =
    if legs.isEmpty then Left(EmptyVoyages)
    else Right(new Itinerary(legs.map(_.voyageNumber), legs))

  def unsafeFrom(voyageNumbers: List[String]): Itinerary =
    new Itinerary(voyageNumbers, Nil)

  def unsafeFromLegs(legs: List[ItineraryLeg]): Itinerary =
    new Itinerary(legs.map(_.voyageNumber), legs)
