package cargotracker.shipper.domain.model.aggregates

import cargotracker.shared.domain.{ShipperId, ShipperType}
import cargotracker.shipper.domain.model.valueobjects.DiscountRate

/** 荷主集約ルート。個人・法人を 1 つのクラスで表現する（variant フィールドで分岐）。
  *
  *   - 個人: `contractNumber = None`、`discountRate = DiscountRate.zero`
  *   - 法人: `contractNumber = Some(_)`、`discountRate` は 0〜0.30
  */
final case class Shipper private (
    shipperId: ShipperId,
    name: String,
    email: String,
    phone: String,
    address: String,
    shipperType: ShipperType,
    contractNumber: Option[String],
    discountRate: DiscountRate,
    version: Int = 0
):
  /** 永続化からの復元用に DB の version を反映した新インスタンスを返す（楽観ロック）。 */
  def withVersion(v: Int): Shipper = copy(version = v)

object Shipper:

  sealed trait Error
  case object InvalidName extends Error
  case object InvalidEmail extends Error

  private val EmailPattern =
    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".r

  /** 個人荷主を生成する。 */
  def individual(
      shipperId: ShipperId,
      name: String,
      email: String,
      phone: String,
      address: String
  ): Either[Error, Shipper] =
    for
      n <- validateName(name)
      e <- validateEmail(email)
    yield Shipper(
      shipperId = shipperId,
      name = n,
      email = e,
      phone = phone,
      address = address,
      shipperType = ShipperType.Individual,
      contractNumber = None,
      discountRate = DiscountRate.zero
    )

  /** 法人荷主を生成する。 */
  def corporate(
      shipperId: ShipperId,
      name: String,
      email: String,
      phone: String,
      address: String,
      contractNumber: String,
      discountRate: DiscountRate
  ): Either[Error, Shipper] =
    for
      n <- validateName(name)
      e <- validateEmail(email)
    yield Shipper(
      shipperId = shipperId,
      name = n,
      email = e,
      phone = phone,
      address = address,
      shipperType = ShipperType.Corporate,
      contractNumber = Some(contractNumber),
      discountRate = discountRate
    )

  private def validateName(name: String): Either[Error, String] =
    Option(name).map(_.trim).filter(_.nonEmpty).toRight(InvalidName)

  private def validateEmail(email: String): Either[Error, String] =
    Option(email)
      .filter(e => EmailPattern.findFirstIn(e).isDefined)
      .toRight(InvalidEmail)
