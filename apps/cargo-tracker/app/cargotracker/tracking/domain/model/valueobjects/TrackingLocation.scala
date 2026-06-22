package cargotracker.tracking.domain.model.valueobjects

/** Tracking Context 内で扱う位置情報（コンテキスト分離 / domain-model.md L703）。 */
final case class TrackingLocation(unLocode: String, name: Option[String] = None)

object TrackingLocation:
  def of(unLocode: String): TrackingLocation = TrackingLocation(unLocode, None)
