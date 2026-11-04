package cargotracker.booking.usecase

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{Clock, InvalidValue, PortCode, Principal, Ulid}
import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

/** 貨物予約の登録（US04）。`bookingId` は呼び出し側（ゲートウェイ）が生成した ULID（ADR-003）。 */
final case class RegisterBookingInput(
    idempotencyKey: String,
    bookingId: String,
    shipperId: String,
    itemName: String,
    weightKg: BigDecimal,
    lengthCm: BigDecimal,
    widthCm: BigDecimal,
    heightCm: BigDecimal,
    quantity: Int,
    origin: String,
    destination: String,
    preferredHandover: LocalDate,
    deadline: LocalDate
)

/** 応答に、集約の ID と最後のイベントの版を含める（画面が版が追いつくまで待つ。BE 6.4）。 */
final case class RegisterBookingOutput(bookingId: BookingId, number: BookingNumber, version: Long)

/** 認可 → 冪等性 → 入力検証 → 荷主の存在確認（外部の事実）→ 番号の発行 → 集約への登録の順。 */
final class RegisterBooking(
    shippers: ShipperRepository,
    ports: PortCatalog,
    idempotency: IdempotencyStore,
    bookings: BookingAggregates,
    numbers: BookingNumberIssuer,
    ids: IdGenerator,
    clock: Clock
)(using ExecutionContext):

  def execute(
      principal: Principal,
      input: RegisterBookingInput
  ): Future[Either[UseCaseError, RegisterBookingOutput]] =
    if !Permissions.canRegisterBooking(principal.role) then
      Future.successful(Left(UseCaseError.Forbidden))
    else
      Idempotency.validateKey(input.idempotencyKey) match
        case Left(e)    => Future.successful(Left(UseCaseError.ValidationFailed(List(e))))
        case Right(key) =>
          val hash = Idempotency.requestHash(input.copy(idempotencyKey = ""))
          Idempotency.once(idempotency, principal, key, hash)(register(principal, input)).map {
            case Right(IdempotentOutput.BookingRegistered(id, number, version)) =>
              Right(RegisterBookingOutput(id, number, version))
            case Right(_) => Left(UseCaseError.IdempotencyKeyReused)
            case Left(e)  => Left(e)
          }

  private final case class Validated(
      bookingId: BookingId,
      shipperId: ShipperId,
      cargo: CargoSpec,
      transport: TransportRequest
  )

  private def validate(input: RegisterBookingInput): Either[List[InvalidValue], Validated] =
    val bookingId = Ulid.parse(input.bookingId, "bookingId").map(BookingId(_))
    val shipperId = Ulid.parse(input.shipperId, "shipperId").map(ShipperId(_))
    val origin = PortCode.parse(input.origin, "origin")
    val dest = PortCode.parse(input.destination, "destination")
    val cargo = for
      d <- Dimensions.create(input.lengthCm, input.widthCm, input.heightCm)
      c <- CargoCommon.create(input.itemName, input.weightKg, d, input.quantity)
    yield CargoSpec.General(c)
    val transport = for
      o <- origin
      d <- dest
      t <- TransportRequest.create(o, d, input.preferredHandover, input.deadline)
    yield t
    // 見つかった不備はまとめて返す。項目名は出発地・目的地を区別する（画面が正しい欄に出せるように）
    val errors = List(
      bookingId.left.toOption,
      shipperId.left.toOption,
      cargo.left.toOption,
      origin.left.toOption,
      dest.left.toOption,
      if origin.isRight && dest.isRight then transport.left.toOption else None
    ).flatten
    (bookingId, shipperId, cargo, transport) match
      case (Right(b), Right(s), Right(c), Right(t)) => Right(Validated(b, s, c, t))
      case _                                        => Left(errors)

  private def register(
      principal: Principal,
      input: RegisterBookingInput
  ): Future[Either[UseCaseError, IdempotentOutput]] =
    validate(input) match
      case Left(errors) => Future.successful(Left(UseCaseError.ValidationFailed(errors)))
      case Right(v)     =>
        unknownPorts(v.transport).flatMap {
          case Nil     => registerFor(principal, v)
          case unknown =>
            Future.successful(Left(UseCaseError.ValidationFailed(unknown)))
        }

  /** 形式は正しいが参照データに無い港。書き込み側で拒む（拒まないと、投影が外部キー違反で永久に失敗する）。 */
  private def unknownPorts(t: TransportRequest): Future[List[InvalidValue]] =
    for
      o <- ports.exists(t.origin)
      d <- ports.exists(t.destination)
    yield List(
      Option.unless(o)(InvalidValue("origin", "登録されていない港")),
      Option.unless(d)(InvalidValue("destination", "登録されていない港"))
    ).flatten

  private def registerFor(
      principal: Principal,
      v: Validated
  ): Future[Either[UseCaseError, IdempotentOutput]] =
    shippers.findById(v.shipperId).flatMap {
      case None          => Future.successful(Left(UseCaseError.ShipperNotFound(v.shipperId)))
      case Some(shipper) =>
        numbers.next().flatMap { number =>
          val command = BookingCommand.RegisterBooking(
            EventMeta(ids.next(), clock.now(), Some(principal.userId)),
            v.bookingId,
            number,
            ShipperRef(shipper.id, shipper.kind),
            v.cargo,
            v.transport
          )
          bookings.handle(v.bookingId, command).map {
            case Left(e)       => Left(UseCaseError.Booking(e))
            case Right(result) =>
              Right(IdempotentOutput.BookingRegistered(v.bookingId, number, result.lastVersion))
          }
        }
    }
