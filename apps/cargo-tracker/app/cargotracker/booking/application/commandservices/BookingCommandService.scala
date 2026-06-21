package cargotracker.booking.application.commandservices

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{
  BookingId,
  CargoSpec,
  HazardousDeclaration,
  RefrigerationSpec,
  RouteSpecification,
  TemperatureUnit
}
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
      refrigeration <- buildRefrigeration(command).left
        .map(_ => "温度範囲が不正です（最低 ≤ 最高）")
      spec <- CargoSpec
        .create(
          cargoType = cargoType,
          weight = weight,
          description = command.description.filter(_.nonEmpty),
          quantity = command.quantity,
          hazardous = hazardous,
          refrigeration = refrigeration
        )
        .left
        .map {
          case CargoSpec.HazardousDeclarationRequired =>
            "危険物の貨物には UN 番号・正式輸送品名・危険物クラスが必須です"
          case CargoSpec.RefrigerationSpecRequired =>
            "冷凍・冷蔵貨物には温度範囲（最低・最高・単位）が必須です"
        }
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

  /** 経路設計者への引き渡し（US06）。
    *
    *   - 予約が存在しない場合は `Left("予約 BK-... が見つかりません")`
    *   - 状態遷移違反は `Left("現在の状態 ... から RouteProposed への遷移はできません")`
    *   - 成功時は新状態の Cargo を保存して返す
    */
  def assignToRouting(bookingId: String): Either[String, Cargo] =
    val parsed = BookingId(bookingId).left
      .map(_ => s"予約 ID の形式が不正です: $bookingId")
    for
      id <- parsed
      cargo <- repository.findById(id).toRight(s"予約 $bookingId が見つかりません")
      next <- cargo.assignToRouting().left.map {
        case Cargo.InvalidStatusTransition(from, to) =>
          s"現在の状態 $from から $to への遷移はできません"
        case _ => s"予約 $bookingId の引き渡しに失敗しました"
      }
    yield
      repository.save(next)
      next

  private def buildRefrigeration(
      command: BookCargoCommand
  ): Either[RefrigerationSpec.Error, Option[RefrigerationSpec]] =
    (
      command.refrigerationMinTemp,
      command.refrigerationMaxTemp,
      command.refrigerationUnit
        .flatMap(TemperatureUnit.fromName)
    ) match
      case (Some(minT), Some(maxT), Some(unit)) =>
        RefrigerationSpec(minT, maxT, unit).map(Some(_))
      case _ =>
        Right(None)

/** 貨物予約コマンド（US04 + US05）。Controller から受け取るフォーム値の DTO 相当。 */
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
    hazardousProperName: Option[String],
    refrigerationMinTemp: Option[Int] = None,
    refrigerationMaxTemp: Option[Int] = None,
    refrigerationUnit: Option[String] = None
)
