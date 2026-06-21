package cargotracker.booking.domain.model.valueobjects

/** 予約の業務キー識別子。`BK-NNNNNN` 形式（数字 6 桁）。 */
opaque type BookingId = String

object BookingId:
  sealed trait Error
  case object EmptyValue extends Error
  case object InvalidFormat extends Error

  private val Pattern = "^BK-[0-9]{6}$".r

  def apply(raw: String): Either[Error, BookingId] =
    Option(raw) match
      case None | Some("") => Left(EmptyValue)
      case Some(v) =>
        if Pattern.matches(v) then Right(v) else Left(InvalidFormat)

  def unsafeFrom(raw: String): BookingId = raw

  extension (id: BookingId) def value: String = id

/** 予約の状態（domain-model.md ビジネスルール 4 準拠）。
  *
  * 遷移規約: Preliminary → RouteProposed → Confirmed → TrackingIssued → InTransit → Delivered → Settled の順に進む。 Preliminary
  * / RouteProposed / Confirmed からは Cancelled に遷移可能。
  */
enum BookingStatus:
  case Preliminary
  case RouteProposed
  case Confirmed
  case TrackingIssued
  case InTransit
  case Delivered
  case Settled
  case Cancelled

  /** 指定された次状態へ遷移可能か判定する。違反時のエラー化は呼び出し側で行う。 */
  def canTransitionTo(next: BookingStatus): Boolean = (this, next) match
    case (Preliminary, RouteProposed) => true
    case (RouteProposed, Confirmed) => true
    case (Confirmed, TrackingIssued) => true
    case (TrackingIssued, InTransit) => true
    case (InTransit, Delivered) => true
    case (Delivered, Settled) => true
    case (Preliminary, Cancelled) => true
    case (RouteProposed, Cancelled) => true
    case (Confirmed, Cancelled) => true
    case _ => false

object BookingStatus:
  def fromName(name: String): Option[BookingStatus] =
    values.find(_.toString.equalsIgnoreCase(name))
