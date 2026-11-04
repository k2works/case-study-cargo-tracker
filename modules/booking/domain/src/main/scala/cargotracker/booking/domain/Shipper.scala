package cargotracker.booking.domain

import cargotracker.sharedkernel.InvalidValue

enum ShipperError:
  /** 同じメールアドレスの荷主が既にいる（警告。承知して続けるなら登録できる）。 */
  case DuplicateEmail(existing: ShipperId)
  case Invalid(error: InvalidValue)

/** 荷主（CRUD の集約。イベントを持たない。ADR-004）。 */
final case class Shipper(
    id: ShipperId,
    kind: ShipperKind,
    name: ShipperName,
    address: Address,
    contact: ContactNumber,
    email: EmailAddress
)

object Shipper:

  /** `duplicateOfEmail` は永続化の側（リポジトリ）が調べた「同じメールの既存の荷主」（外部の事実）。
    * 重複していて承知していなければ拒み、承知していれば登録する（一意制約にしない。US02 AC-02）。
    */
  def register(
      id: ShipperId,
      name: ShipperName,
      address: Address,
      contact: ContactNumber,
      email: EmailAddress,
      kind: ShipperKind,
      duplicateOfEmail: Option[ShipperId],
      acknowledgedDuplicate: Boolean
  ): Either[ShipperError, Shipper] =
    duplicateOfEmail match
      case Some(existing) if !acknowledgedDuplicate => Left(ShipperError.DuplicateEmail(existing))
      case _ => Right(Shipper(id, kind, name, address, contact, email))
