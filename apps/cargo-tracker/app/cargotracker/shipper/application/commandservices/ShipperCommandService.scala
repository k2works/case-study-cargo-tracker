package cargotracker.shipper.application.commandservices

import cargotracker.shared.domain.ShipperType
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.shipper.domain.model.valueobjects.DiscountRate

import javax.inject.{Inject, Singleton}

/** 荷主登録コマンドサービス（US02・US03）。
  *
  *   - 個人荷主は [[RegisterShipperCommand.Individual]]、法人荷主は [[RegisterShipperCommand.Corporate]] を受け付ける
  *   - 採番（`ShipperRepository.nextIdentity`）・生成（`Shipper.individual` / `Shipper.corporate`）・永続化（`save`）をまとめて実行する
  *   - ドメインバリデーション失敗は文字列メッセージにマッピングして返す
  */
@Singleton
class ShipperCommandService @Inject() (repository: ShipperRepository):

  def register(command: RegisterShipperCommand): Either[String, Shipper] =
    val build: Either[String, Shipper] = command match
      case RegisterShipperCommand.Individual(name, email, phone, address) =>
        Shipper
          .individual(repository.nextIdentity(), name, email, phone, address)
          .left
          .map(err => s"荷主の生成に失敗しました: $err")

      case RegisterShipperCommand.Corporate(name, email, phone, address, contractNumber, discountRate) =>
        Shipper
          .corporate(
            repository.nextIdentity(),
            name,
            email,
            phone,
            address,
            contractNumber,
            discountRate
          )
          .left
          .map(err => s"荷主の生成に失敗しました: $err")

    build.map { shipper =>
      repository.save(shipper)
      shipper
    }

/** 荷主登録コマンド（US02 個人 / US03 法人）。 */
enum RegisterShipperCommand:
  case Individual(
      name: String,
      email: String,
      phone: String,
      address: String
  )
  case Corporate(
      name: String,
      email: String,
      phone: String,
      address: String,
      contractNumber: String,
      discountRate: DiscountRate
  )

object RegisterShipperCommand:

  /** 文字列形式の `shipperType` から適切なコマンドを生成する。法人で割引率が未指定の場合は 0% とする。 */
  def from(
      shipperType: String,
      name: String,
      email: String,
      phone: String,
      address: String,
      contractNumber: Option[String],
      discountRate: Option[Double]
  ): Either[String, RegisterShipperCommand] =
    ShipperType.fromName(shipperType) match
      case Some(ShipperType.Individual) =>
        Right(Individual(name, email, phone, address))
      case Some(ShipperType.Corporate) =>
        val rate = discountRate
          .flatMap(d => DiscountRate(d).toOption)
          .getOrElse(DiscountRate.zero)
        Right(
          Corporate(name, email, phone, address, contractNumber.getOrElse(""), rate)
        )
      case None =>
        Left("荷主種別が不正です")
