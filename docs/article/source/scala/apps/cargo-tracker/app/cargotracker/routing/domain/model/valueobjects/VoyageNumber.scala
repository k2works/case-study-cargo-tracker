package cargotracker.routing.domain.model.valueobjects

/** 航海の業務キー識別子（domain-model.md 準拠）。 形式は運送会社プレフィックス + 数字 / 英数字。例: `VY-001`、`MAEU-12345`。
  *
  * IT2 では緩めの正規表現で受け付ける。 IT3 以降に運送会社別の厳密な形式を導入する。
  */
opaque type VoyageNumber = String

object VoyageNumber:
  sealed trait Error
  case object EmptyValue extends Error
  case object InvalidFormat extends Error

  private val Pattern = "^[A-Z][A-Z0-9-]{1,19}$".r

  def apply(raw: String): Either[Error, VoyageNumber] =
    Option(raw) match
      case None | Some("") => Left(EmptyValue)
      case Some(v) =>
        if Pattern.matches(v) then Right(v) else Left(InvalidFormat)

  def unsafeFrom(raw: String): VoyageNumber = raw

  extension (vn: VoyageNumber) def value: String = vn
