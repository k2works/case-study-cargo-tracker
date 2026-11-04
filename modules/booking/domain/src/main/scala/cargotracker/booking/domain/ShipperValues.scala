package cargotracker.booking.domain

import cargotracker.sharedkernel.InvalidValue

/** 空にできない文字列の値オブジェクトの共通部分。前後の空白を除き、長さの上限を持つ。 */
private object NonEmptyText:
  def parse(field: String, raw: String, max: Int): Either[InvalidValue, String] =
    val v = raw.trim
    if v.isEmpty then Left(InvalidValue(field, "空にできない"))
    else if v.length > max then Left(InvalidValue(field, s"$max 文字以内にする"))
    else Right(v)

final case class ShipperName private (value: String)
object ShipperName:
  def parse(raw: String): Either[InvalidValue, ShipperName] =
    NonEmptyText.parse("name", raw, 200).map(ShipperName(_))

final case class Address private (value: String)
object Address:
  def parse(raw: String): Either[InvalidValue, Address] =
    NonEmptyText.parse("address", raw, 500).map(Address(_))

/** 連絡先（電話番号など）。形式は検証しない（受入条件に無い。AI の仮定）。 */
final case class ContactNumber private (value: String)
object ContactNumber:
  def parse(raw: String): Either[InvalidValue, ContactNumber] =
    NonEmptyText.parse("contact", raw, 100).map(ContactNumber(_))

/** メールアドレス。前後の空白を除き小文字にそろえる（重複の判定のため）。 */
final case class EmailAddress private (value: String)
object EmailAddress:
  private val Pattern = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$".r

  def parse(raw: String): Either[InvalidValue, EmailAddress] =
    NonEmptyText.parse("email", raw, 254).flatMap { v =>
      val lower = v.toLowerCase
      Either.cond(
        Pattern.matches(lower),
        EmailAddress(lower),
        InvalidValue("email", "メールアドレスの形式ではない")
      )
    }

/** 荷主種別。Bolt 1 は個人のみ。法人（契約番号・割引率）は Bolt 13 で足す。 */
enum ShipperKind:
  case Individual
