package cargotracker.booking.queryadapter

import cargotracker.sharedkernel.Principal
import io.circe.Json
import sangria.execution.{ExceptionHandler, Executor, HandledException}
import sangria.marshalling.circe.*
import sangria.parser.QueryParser
import sangria.schema.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** GraphQL の実行コンテキスト（呼び出した利用者）。 */
final case class GraphQLContext(principal: Principal)

/** 読み取りの失敗を GraphQL のエラー（`extensions.code`）にするための例外。 */
final class QueryFailure(val error: QueryError) extends RuntimeException(s"${error.code}")

/** 予約 Unit の GraphQL（Query）。リゾルバは翻訳だけを行い、認可は QueryService の入口で行う（バックエンド 8.1）。 */
final class BookingGraphQL(service: QueryService)(using ec: ExecutionContext):

  private type Ctx = GraphQLContext

  private def lift[A](f: Future[Either[QueryError, A]]): Future[A] =
    f.flatMap {
      case Right(a) => Future.successful(a)
      case Left(e)  => Future.failed(new QueryFailure(e))
    }

  private val ShipperSummaryType = ObjectType(
    "ShipperSummary",
    fields[Ctx, ShipperSummary](
      Field("id", StringType, resolve = _.value.id),
      Field("name", StringType, resolve = _.value.name),
      Field("email", StringType, resolve = _.value.email),
      Field("kind", StringType, resolve = _.value.kind)
    )
  )

  private val ShipperDetailType = ObjectType(
    "Shipper",
    fields[Ctx, ShipperDetail](
      Field("id", StringType, resolve = _.value.id),
      Field("kind", StringType, resolve = _.value.kind),
      Field("name", StringType, resolve = _.value.name),
      Field("address", StringType, resolve = _.value.address),
      Field("contact", StringType, resolve = _.value.contact),
      Field("email", StringType, resolve = _.value.email)
    )
  )

  private val BookingSummaryType = ObjectType(
    "BookingSummary",
    fields[Ctx, BookingSummary](
      Field("id", StringType, resolve = _.value.id),
      Field("number", StringType, resolve = _.value.number),
      Field("shipperId", StringType, resolve = _.value.shipperId),
      Field("status", StringType, resolve = _.value.status),
      Field("origin", StringType, resolve = _.value.origin),
      Field("destination", StringType, resolve = _.value.destination),
      Field("deadline", StringType, resolve = _.value.deadline),
      // 画面は「応答の版以上になるまで」再取得する（バックエンド 6.4）
      Field("version", LongType, resolve = _.value.version),
      Field("availableCommands", ListType(StringType), resolve = _.value.availableCommands)
    )
  )

  private val CargoType = ObjectType(
    "Cargo",
    fields[Ctx, CargoView](
      Field("itemName", StringType, resolve = _.value.itemName),
      Field("weightKg", StringType, resolve = _.value.weightKg),
      Field("quantity", IntType, resolve = _.value.quantity)
    )
  )

  private val HistoryType = ObjectType(
    "HistoryEntry",
    fields[Ctx, HistoryEntry](
      Field("eventId", StringType, resolve = _.value.eventId),
      Field("kind", StringType, resolve = _.value.kind),
      Field("occurredAt", StringType, resolve = _.value.occurredAt.toString),
      Field("actorUserId", OptionType(StringType), resolve = _.value.actorUserId)
    )
  )

  private val BookingDetailType = ObjectType(
    "Booking",
    fields[Ctx, BookingDetail](
      Field("id", StringType, resolve = _.value.summary.id),
      Field("number", StringType, resolve = _.value.summary.number),
      Field("shipperId", StringType, resolve = _.value.summary.shipperId),
      Field("status", StringType, resolve = _.value.summary.status),
      Field("version", LongType, resolve = _.value.summary.version),
      Field("availableCommands", ListType(StringType), resolve = _.value.summary.availableCommands),
      Field("origin", StringType, resolve = _.value.summary.origin),
      Field("destination", StringType, resolve = _.value.summary.destination),
      Field("deadline", StringType, resolve = _.value.summary.deadline),
      Field("preferredHandover", StringType, resolve = _.value.preferredHandover),
      Field("cargo", CargoType, resolve = _.value.cargo),
      Field("history", ListType(HistoryType), resolve = _.value.history)
    )
  )

  private val IdArg = Argument("id", StringType)
  private val KeywordArg = Argument("keyword", OptionInputType(StringType))
  private val StatusArg = Argument("status", OptionInputType(StringType))
  private val ShipperArg = Argument("shipperId", OptionInputType(StringType))

  private val QueryType = ObjectType(
    "Query",
    fields[Ctx, Unit](
      Field(
        "shippers",
        ListType(ShipperSummaryType),
        arguments = KeywordArg :: Nil,
        resolve = c => lift(service.searchShippers(c.ctx.principal, c.arg(KeywordArg)))
      ),
      Field(
        "shipper",
        ShipperDetailType,
        arguments = IdArg :: Nil,
        resolve = c => lift(service.shipper(c.ctx.principal, c.arg(IdArg)))
      ),
      Field(
        "bookings",
        ListType(BookingSummaryType),
        arguments = StatusArg :: ShipperArg :: Nil,
        resolve =
          c => lift(service.listBookings(c.ctx.principal, c.arg(StatusArg), c.arg(ShipperArg)))
      ),
      Field(
        "booking",
        BookingDetailType,
        arguments = IdArg :: Nil,
        resolve = c => lift(service.booking(c.ctx.principal, c.arg(IdArg)))
      )
    )
  )

  val schema: Schema[Ctx, Unit] = Schema(QueryType)

  /** 業務エラーはコード（FORBIDDEN・NOT_FOUND・INVALID_INPUT）にし、それ以外の例外は内部の詳細を出さない。 */
  private val exceptionHandler = ExceptionHandler {
    case (m, e: QueryFailure) =>
      val message = e.error match
        case QueryError.InvalidInput(field, reason) => s"$field: $reason"
        case other                                  => other.code
      HandledException(message, Map("code" -> m.scalarNode(e.error.code, "String", Set.empty)))
    case (m, _) =>
      HandledException("内部エラー", Map("code" -> m.scalarNode("INTERNAL", "String", Set.empty)))
  }

  /** クエリを実行して、GraphQL の応答（`data`・`errors`）を返す。文法や検証の誤りも応答の `errors` にする。 */
  def execute(principal: Principal, query: String, variables: Json): Future[Json] =
    QueryParser.parse(query) match
      case Failure(e)        => Future.successful(errorJson("SYNTAX_ERROR", e.getMessage))
      case Success(document) =>
        Executor
          .execute(
            schema,
            document,
            GraphQLContext(principal),
            variables = variables,
            exceptionHandler = exceptionHandler
          )
          .recover { case e: sangria.execution.ExecutionError =>
            errorJson("EXECUTION_ERROR", e.getMessage)
          }
          .recover { case e: sangria.execution.ErrorWithResolver => e.resolveError }

  private def errorJson(code: String, message: String): Json =
    Json.obj(
      "errors" -> Json.arr(
        Json.obj(
          "message" -> Json.fromString(message),
          "extensions" -> Json.obj("code" -> Json.fromString(code))
        )
      )
    )
