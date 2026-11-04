package cargotracker.booking.usecase

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{Clock, InvalidValue, Principal}
import scala.concurrent.{ExecutionContext, Future}

final case class RegisterShipperInput(
    idempotencyKey: String,
    name: String,
    address: String,
    contact: String,
    email: String,
    acknowledgedDuplicate: Boolean
)

final case class RegisterShipperOutput(shipperId: ShipperId)

/** 荷主を登録する（US02）。認可 → 冪等性 → 入力検証 → 重複の確認 → 登録の順。 */
final class RegisterShipper(
    shippers: ShipperRepository,
    idempotency: IdempotencyStore,
    ids: IdGenerator,
    clock: Clock
)(using ExecutionContext):

  def execute(
      principal: Principal,
      input: RegisterShipperInput
  ): Future[Either[UseCaseError, RegisterShipperOutput]] =
    if !Permissions.canRegisterShipper(principal.role) then
      Future.successful(Left(UseCaseError.Forbidden))
    else
      Idempotency.validateKey(input.idempotencyKey) match
        case Left(e)    => Future.successful(Left(UseCaseError.ValidationFailed(List(e))))
        case Right(key) =>
          val hash = Idempotency.requestHash(input.copy(idempotencyKey = ""))
          Idempotency.once(idempotency, principal, key, hash)(register(principal, input)).map {
            case Right(IdempotentOutput.ShipperRegistered(id)) => Right(RegisterShipperOutput(id))
            case Right(other) => Left(UseCaseError.IdempotencyKeyReused)
            case Left(e)      => Left(e)
          }

  private def register(
      principal: Principal,
      input: RegisterShipperInput
  ): Future[Either[UseCaseError, IdempotentOutput]] =
    val parsed = for
      n <- ShipperName.parse(input.name)
      a <- Address.parse(input.address)
      c <- ContactNumber.parse(input.contact)
      e <- EmailAddress.parse(input.email)
    yield (n, a, c, e)
    // 入力の不備は、見つかった項目をまとめて返す
    val errors = List(
      ShipperName.parse(input.name).left.toOption,
      Address.parse(input.address).left.toOption,
      ContactNumber.parse(input.contact).left.toOption,
      EmailAddress.parse(input.email).left.toOption
    ).flatten
    parsed match
      case Left(_) => Future.successful(Left(UseCaseError.ValidationFailed(errors)))
      case Right((name, address, contact, email)) =>
        shippers.findByEmail(email).flatMap { duplicate =>
          Shipper.register(
            ShipperId(ids.next()),
            name,
            address,
            contact,
            email,
            ShipperKind.Individual,
            duplicate,
            input.acknowledgedDuplicate
          ) match
            case Left(ShipperError.DuplicateEmail(existing)) =>
              Future.successful(Left(UseCaseError.DuplicateEmail(existing)))
            case Left(ShipperError.Invalid(e)) =>
              Future.successful(Left(UseCaseError.ValidationFailed(List(e))))
            case Right(shipper) =>
              shippers
                .save(shipper, AuditStamp(principal.userId, clock.now()))
                .map(_ => Right(IdempotentOutput.ShipperRegistered(shipper.id)))
        }
