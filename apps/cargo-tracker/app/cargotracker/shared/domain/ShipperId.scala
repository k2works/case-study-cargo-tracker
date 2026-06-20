package cargotracker.shared.domain

/** 荷主の業務キー識別子。`SH-NNNNNN` 形式（数字 6 桁）。 */
opaque type ShipperId = String

object ShipperId:

  sealed trait Error
  case object EmptyValue extends Error
  case object InvalidFormat extends Error

  private val Pattern = "^SH-[0-9]{6}$".r

  /** 入力文字列を検証して ShipperId を生成する。 */
  def apply(raw: String): Either[Error, ShipperId] =
    Option(raw) match
      case None | Some("") => Left(EmptyValue)
      case Some(v) =>
        if Pattern.matches(v) then Right(v) else Left(InvalidFormat)

  /** 永続化からの読み戻し用（バリデーションを省く） */
  def unsafeFrom(raw: String): ShipperId = raw

  extension (id: ShipperId) def value: String = id

/** 荷主種別。 */
enum ShipperType:
  case Individual
  case Corporate

object ShipperType:
  def fromName(name: String): Option[ShipperType] =
    values.find(_.toString.equalsIgnoreCase(name))
