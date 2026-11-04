package cargotracker.booking.queryadapter

import cargotracker.sharedkernel.{Principal, Role, Ulid}
import scala.concurrent.{ExecutionContext, Future}

/** 読み取りの失敗。GraphQL ではエラーコードに写す（500 にしない）。 */
enum QueryError:
  case Forbidden
  case NotFound
  case InvalidInput(field: String, reason: String)

  def code: String = this match
    case Forbidden          => "FORBIDDEN"
    case NotFound           => "NOT_FOUND"
    case InvalidInput(_, _) => "INVALID_INPUT"

/** 読み取りの認可（A8：ロール × 操作の表。ここが単一の出所）。 */
object QueryPermissions:
  /** 荷主の情報は個人情報を含む：営業担当者だけ（UI 設計 4.2：SC-20〜22）。 */
  def canViewShippers(role: Role): Boolean = role == Role.Sales

  /** 予約の一覧・詳細：営業担当者・経路設計者・追跡管理者（SC-25・27）。 */
  def canViewBookings(role: Role): Boolean =
    Set(Role.Sales, Role.RouteDesigner, Role.TrackingManager)(role)

/** 認可 → 入力の検証 → 問い合わせの順（認可は入力検証より先。権限の無い相手に入力の仕様を教えない）。 */
final class QueryService(shippers: ShipperQueries, bookings: BookingQueries)(using
    ExecutionContext
):
  private val MaxRows = 100
  private val statuses =
    Set(
      "RECEIVED",
      "ROUTE_PROPOSAL",
      "CONFIRMED",
      "TRACKING_ISSUED",
      "IN_TRANSIT",
      "DELIVERED",
      "SETTLED",
      "CANCELLED"
    )

  private def ulid(field: String, raw: String): Either[QueryError, String] =
    Ulid.parse(raw).map(_.value).left.map(e => QueryError.InvalidInput(field, e.reason))

  def searchShippers(
      p: Principal,
      keyword: Option[String]
  ): Future[Either[QueryError, List[ShipperSummary]]] =
    if !QueryPermissions.canViewShippers(p.role) then Future.successful(Left(QueryError.Forbidden))
    else shippers.search(keyword, MaxRows).map(Right(_))

  def shipper(p: Principal, id: String): Future[Either[QueryError, ShipperDetail]] =
    if !QueryPermissions.canViewShippers(p.role) then Future.successful(Left(QueryError.Forbidden))
    else
      ulid("id", id) match
        case Left(e)    => Future.successful(Left(e))
        case Right(key) => shippers.detail(key).map(_.toRight(QueryError.NotFound))

  def listBookings(
      p: Principal,
      status: Option[String],
      shipperId: Option[String]
  ): Future[Either[QueryError, List[BookingSummary]]] =
    if !QueryPermissions.canViewBookings(p.role) then Future.successful(Left(QueryError.Forbidden))
    else
      val checked = for
        s <- status.fold[Either[QueryError, Option[String]]](Right(None))(v =>
          Either.cond(statuses(v), Some(v), QueryError.InvalidInput("status", "予約状態ではない"))
        )
        sh <- shipperId.fold[Either[QueryError, Option[String]]](Right(None))(v =>
          ulid("shipperId", v).map(Some(_))
        )
      yield (s, sh)
      checked match
        case Left(e)        => Future.successful(Left(e))
        case Right((s, sh)) => bookings.list(s, sh, MaxRows).map(Right(_))

  def booking(p: Principal, id: String): Future[Either[QueryError, BookingDetail]] =
    if !QueryPermissions.canViewBookings(p.role) then Future.successful(Left(QueryError.Forbidden))
    else
      ulid("id", id) match
        case Left(e)    => Future.successful(Left(e))
        case Right(key) => bookings.detail(key).map(_.toRight(QueryError.NotFound))
