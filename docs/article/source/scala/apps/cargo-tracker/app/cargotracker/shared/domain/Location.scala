package cargotracker.shared.domain

/** 場所（港湾・倉庫等）。UnLocode 5 桁で識別する。
  *
  *   - 共有カーネル。Booking / Estimation / Routing / Tracking で参照される
  *   - `name` は表示用の人間可読名（オプション）
  */
final case class Location private (unLocode: String, name: String)

object Location:

  sealed trait Error
  case object InvalidUnLocode extends Error

  private val Pattern = "^[A-Z]{5}$".r

  def apply(unLocode: String, name: String = ""): Either[Error, Location] =
    Option(unLocode).filter(Pattern.matches) match
      case Some(code) => Right(new Location(code, name))
      case None => Left(InvalidUnLocode)

  /** 永続化からの読み戻し用 */
  def unsafeFrom(unLocode: String, name: String = ""): Location =
    new Location(unLocode, name)
