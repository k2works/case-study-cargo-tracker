package cargotracker.booking.application.commandservices

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{CargoSpec, HazardousDeclaration, RouteSpecification}
import cargotracker.shared.domain.{CargoType, Location, ShipperId, Weight}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

/** 貨物予約コマンドサービス（US04）。
  *
  * UnLocode / 貨物種別 / 重量 / 経路仕様の検証、危険物申告の組み立て、 [[Cargo]] 集約の生成（ACL `ShipperExistenceChecker`
  * 経由で荷主存在検証）と永続化までを一連のユースケースとして実行する。
  */
@Singleton
class BookingCommandService @Inject() (
    repository: CargoRepository,
    shipperChecker: ShipperExistenceChecker
):

  def book(command: BookCargoCommand): Either[String, Cargo] =
    val result = for
      origin <- Location(command.origin).left.map(_ => "出発地の UnLocode 形式が不正です")
      destination <- Location(command.destination).left
        .map(_ => "目的地の UnLocode 形式が不正です")
      cargoType <- CargoType
        .fromName(command.cargoType)
        .toRight("貨物種別が不正です")
      weight <- Weight(command.weightKg).left.map(_ => "重量が不正です")
      routeSpec <- RouteSpecification(origin, destination, command.arrivalDeadline).left
        .map(_ => "出発地と目的地が同じです")
      hazardous = for
        hc <- command.hazardousClass.filter(_.nonEmpty)
        un <- command.hazardousUnNumber.filter(_.nonEmpty)
        psn <- command.hazardousProperName.filter(_.nonEmpty)
      yield HazardousDeclaration(hc, un, psn)
      spec = CargoSpec(
        cargoType = cargoType,
        weight = weight,
        description = command.description.filter(_.nonEmpty),
        quantity = command.quantity,
        hazardous = hazardous
      )
      cargo <- Cargo
        .book(
          repository.nextIdentity(),
          ShipperId.unsafeFrom(command.shipperCode),
          routeSpec,
          spec,
          shipperChecker
        )
        .left
        .map(_ => s"荷主 ${command.shipperCode} が見つかりません")
    yield cargo

    result.map { cargo =>
      repository.save(cargo)
      cargo
    }

/** 貨物予約コマンド（US04）。Controller から受け取るフォーム値の DTO 相当。 */
final case class BookCargoCommand(
    shipperCode: String,
    origin: String,
    destination: String,
    arrivalDeadline: LocalDate,
    cargoType: String,
    weightKg: Long,
    description: Option[String],
    quantity: Option[Int],
    hazardousClass: Option[String],
    hazardousUnNumber: Option[String],
    hazardousProperName: Option[String]
)
