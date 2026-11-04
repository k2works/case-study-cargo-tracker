package cargotracker.booking.commandadapter

import cargotracker.booking.domain.BookingError
import cargotracker.booking.usecase.*
import cargotracker.sharedkernel.{InvalidValue, Principal}
import io.circe.Json
import java.time.LocalDate
import sangria.execution.{ExceptionHandler, Executor, HandledException}
import sangria.marshalling.ResultMarshaller
import sangria.marshalling.circe.*
import sangria.parser.QueryParser
import sangria.schema.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** ユースケースの失敗を GraphQL のエラー（`extensions.code`）にするための例外。 */
final class MutationFailure(val error: UseCaseError) extends RuntimeException(error.toString)

final case class MutationContext(principal: Principal)

/** 予約 Unit の GraphQL（Mutation）。コマンド側のエンドポイントで、Query とは別（BE-06）。
  * リゾルバは翻訳だけを行い、認可・冪等性・検証はユースケースの入口で行う（バックエンド 4.3・8.1）。 認証は Bolt 2。ここでは呼び出し側が渡した `Principal`
  * を使う（スタブ）。
  */
final class BookingMutationGraphQL(
    registerShipper: RegisterShipper,
    registerBooking: RegisterBooking
)(using
    ec: ExecutionContext
):
  private type Ctx = MutationContext

  private def fail[A](error: UseCaseError): Future[A] = Future.failed(new MutationFailure(error))

  private def lift[A](f: Future[Either[UseCaseError, A]]): Future[A] =
    f.flatMap {
      case Right(a) => Future.successful(a)
      case Left(e)  => fail(e)
    }

  /** GraphQL の引数（文字列）から値への変換。変換できなければ、入力の不備として返す。 */
  private def decimal(field: String, raw: String): Either[UseCaseError, BigDecimal] =
    Try(BigDecimal(raw)).toEither.left.map(_ =>
      UseCaseError.ValidationFailed(List(InvalidValue(field, "数値ではない")))
    )

  private def date(field: String, raw: String): Either[UseCaseError, LocalDate] =
    Try(LocalDate.parse(raw)).toEither.left.map(_ =>
      UseCaseError.ValidationFailed(List(InvalidValue(field, "日付（YYYY-MM-DD）ではない")))
    )

  private val ShipperRegisteredType = ObjectType(
    "RegisteredShipper",
    fields[Ctx, RegisterShipperOutput](
      Field("shipperId", StringType, resolve = _.value.shipperId.value.value)
    )
  )

  private val BookingRegisteredType = ObjectType(
    "RegisteredBooking",
    fields[Ctx, RegisterBookingOutput](
      Field("bookingId", StringType, resolve = _.value.bookingId.value.value),
      Field("number", StringType, resolve = _.value.number.value),
      // 画面は「応答の版以上になるまで」Query を再取得する（バックエンド 6.4）
      Field("version", LongType, resolve = _.value.version)
    )
  )

  private val S = (name: String) => Argument(name, StringType)

  private val MutationType = ObjectType(
    "Mutation",
    fields[Ctx, Unit](
      Field(
        "registerShipper",
        ShipperRegisteredType,
        arguments = List(
          S("idempotencyKey"),
          S("name"),
          S("address"),
          S("contact"),
          S("email"),
          Argument("acknowledgedDuplicate", OptionInputType(BooleanType), defaultValue = false)
        ),
        resolve = c =>
          lift(
            registerShipper.execute(
              c.ctx.principal,
              RegisterShipperInput(
                c.arg[String]("idempotencyKey"),
                c.arg[String]("name"),
                c.arg[String]("address"),
                c.arg[String]("contact"),
                c.arg[String]("email"),
                c.arg[Boolean]("acknowledgedDuplicate")
              )
            )
          )
      ),
      Field(
        "registerBooking",
        BookingRegisteredType,
        arguments = List(
          S("idempotencyKey"),
          S("bookingId"),
          S("shipperId"),
          S("itemName"),
          S("weightKg"),
          S("lengthCm"),
          S("widthCm"),
          S("heightCm"),
          Argument("quantity", IntType),
          S("origin"),
          S("destination"),
          S("preferredHandover"),
          S("deadline")
        ),
        resolve = c =>
          // 認可は入力検証より先：権限の無いロールには、引数の不備を教えずに FORBIDDEN を返す（ユースケースの入口で行う）。
          // 数値・日付に変換できない引数は、ここでは値を保留し、ユースケースの認可の後に不備として返す。
          if !Permissions.canRegisterBooking(c.ctx.principal.role) then fail(UseCaseError.Forbidden)
          else
            val parsed =
              for
                weight <- decimal("weightKg", c.arg[String]("weightKg"))
                length <- decimal("lengthCm", c.arg[String]("lengthCm"))
                width <- decimal("widthCm", c.arg[String]("widthCm"))
                height <- decimal("heightCm", c.arg[String]("heightCm"))
                handover <- date("preferredHandover", c.arg[String]("preferredHandover"))
                deadline <- date("deadline", c.arg[String]("deadline"))
              yield RegisterBookingInput(
                c.arg[String]("idempotencyKey"),
                c.arg[String]("bookingId"),
                c.arg[String]("shipperId"),
                c.arg[String]("itemName"),
                weight,
                length,
                width,
                height,
                c.arg[Int]("quantity"),
                c.arg[String]("origin"),
                c.arg[String]("destination"),
                handover,
                deadline
              )
            parsed match
              case Left(e)      => fail(e)
              case Right(input) => lift(registerBooking.execute(c.ctx.principal, input))
      )
    )
  )

  // Sangria は Query の根が必須。Mutation 専用のエンドポイントなので、疎通確認だけの項目を置く。
  private val QueryType =
    ObjectType("Query", fields[Ctx, Unit](Field("ping", StringType, resolve = _ => "pong")))

  val schema: Schema[Ctx, Unit] = Schema(QueryType, Some(MutationType))

  private def codeOf(error: UseCaseError): String = error match
    case UseCaseError.Forbidden                                     => "FORBIDDEN"
    case UseCaseError.ValidationFailed(_)                           => "VALIDATION_FAILED"
    case UseCaseError.DuplicateEmail(_)                             => "DUPLICATE_EMAIL"
    case UseCaseError.ShipperNotFound(_)                            => "SHIPPER_NOT_FOUND"
    case UseCaseError.IdempotencyKeyReused                          => "IDEMPOTENCY_KEY_REUSED"
    case UseCaseError.Booking(BookingError.InvalidTransition(_, _)) => "INVALID_TRANSITION"

  private val exceptionHandler = ExceptionHandler {
    case (m, e: MutationFailure) =>
      val extra: Map[String, m.Node] = e.error match
        case UseCaseError.ValidationFailed(errors) =>
          Map(
            "fields" -> m.arrayNode(
              errors
                .map(err =>
                  m.mapNode(
                    Seq(
                      "field" -> m.scalarNode(err.field, "String", Set.empty),
                      "reason" -> m.scalarNode(err.reason, "String", Set.empty)
                    )
                  )
                )
                .toVector
            )
          )
        case UseCaseError.DuplicateEmail(existing) =>
          Map("existingShipperId" -> m.scalarNode(existing.value.value, "String", Set.empty))
        case _ => Map.empty
      HandledException(
        codeOf(e.error),
        Map("code" -> m.scalarNode(codeOf(e.error), "String", Set.empty)) ++ extra
      )
    case (m, _) =>
      HandledException("内部エラー", Map("code" -> m.scalarNode("INTERNAL", "String", Set.empty)))
  }

  def execute(principal: Principal, mutation: String, variables: Json): Future[Json] =
    QueryParser.parse(mutation) match
      case Failure(e)        => Future.successful(errorJson("SYNTAX_ERROR", e.getMessage))
      case Success(document) =>
        Executor
          .execute(
            schema,
            document,
            MutationContext(principal),
            variables = variables,
            exceptionHandler = exceptionHandler
          )
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
