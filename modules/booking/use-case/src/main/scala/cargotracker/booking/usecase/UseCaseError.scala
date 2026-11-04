package cargotracker.booking.usecase

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{InvalidValue, Role}

/** ユースケースの失敗。GraphQL ではエラーコードに写す。例外にしない（BE 9.3）。 */
enum UseCaseError:
  case Forbidden
  case ValidationFailed(errors: List[InvalidValue])
  case DuplicateEmail(existing: ShipperId)
  case ShipperNotFound(id: ShipperId)
  case IdempotencyKeyReused
  case Booking(error: BookingError)

/** ロールと操作の対応（A8：ロール × 操作の表。ここが単一の出所）。 */
object Permissions:
  def canRegisterShipper(role: Role): Boolean = role == Role.Sales
  def canRegisterBooking(role: Role): Boolean = role == Role.Sales
