package cargotracker.booking.domain.model.valueobjects

/** 予約に紐付けられた経路（US09 → US11）。
  *
  * Booking Context の値オブジェクト。Routing Context の `RouteCandidateSelection` 集約から applicationservice 層で
  * ACL（変換アダプター）を介して受け取る。
  *
  *   - `voyageNumbers`: 経路を構成する航海番号の順序リスト（1 つ以上）
  */
final case class Itinerary private (voyageNumbers: List[String]):
  require(voyageNumbers.nonEmpty, "経路は 1 航海以上必要")

object Itinerary:
  sealed trait Error
  case object EmptyVoyages extends Error

  def apply(voyageNumbers: List[String]): Either[Error, Itinerary] =
    if voyageNumbers.isEmpty then Left(EmptyVoyages)
    else Right(new Itinerary(voyageNumbers))

  def unsafeFrom(voyageNumbers: List[String]): Itinerary =
    new Itinerary(voyageNumbers)
