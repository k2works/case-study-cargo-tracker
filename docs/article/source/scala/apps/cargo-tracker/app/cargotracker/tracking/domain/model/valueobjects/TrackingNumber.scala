package cargotracker.tracking.domain.model.valueobjects

/** 追跡番号（Tracking Context）。
  *
  *   - 形式: `TN-` + 6 桁ゼロ埋め整数（合計 9 文字）。data-model.md L787 の `VARCHAR(20)` 制約に整合
  *   - 採番ポリシー: ADR 0010 参照
  *   - 採番後の変更は不可（不変条件）
  */
opaque type TrackingNumber = String

object TrackingNumber:
  private val pattern = "^TN-\\d{6,18}$".r

  sealed trait Error
  case object InvalidFormat extends Error

  def apply(raw: String): Either[Error, TrackingNumber] =
    if pattern.matches(raw) then Right(raw)
    else Left(InvalidFormat)

  def unsafeFrom(raw: String): TrackingNumber =
    apply(raw).fold(_ => throw IllegalArgumentException(s"Invalid TrackingNumber: $raw"), identity)

  /** `BIGSERIAL` 値から `TN-000001` 形式の追跡番号を生成する（ADR 0010）。 */
  def fromSequence(seq: Long): TrackingNumber =
    f"TN-$seq%06d"

  extension (tn: TrackingNumber) def value: String = tn
