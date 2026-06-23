package cargotracker.booking.application.commandservices

import cargotracker.booking.application.errors.CargoErrorMessages
import cargotracker.booking.application.notifications.NotificationPayloadJson
import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{
  BookingId,
  CargoSpec,
  HazardousDeclaration,
  Itinerary,
  NotificationPayload,
  NotificationType,
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
    shipperChecker: ShipperExistenceChecker,
    notificationRepository: cargotracker.booking.domain.model.repositories.NotificationLogRepository,
    clock: java.time.Clock
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
        .map {
          case Cargo.UnknownShipper => s"荷主 ${command.shipperCode} が見つかりません"
          case Cargo.InvalidStatusTransition(from, to) =>
            s"予約状態の遷移が不正です（$from → $to）"
          case Cargo.InvalidTrackingNumberFormat(raw) =>
            s"追跡番号の形式が不正です（$raw）"
        }
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
    transition(bookingId, _.assignToRouting(), "引き渡し")

  /** 予約を確定する（US13 / `ConfirmBookingCommand`）。確定成功時は追跡番号発行依頼の通知を記録する。 */
  def confirm(bookingId: String): Either[String, Cargo] =
    transition(bookingId, _.confirm(), "確定").map { cargo =>
      logNotification(
        cargo,
        NotificationType.BookingConfirmed,
        NotificationPayload.BookingConfirmed(cargo.bookingId.value)
      )
      cargo
    }

  /** 経路再設計に戻す（US13 / `ReproposeRouteCommand`）。 */
  def reproposeRoute(bookingId: String): Either[String, Cargo] =
    transition(bookingId, _.reproposeRoute(), "経路再設計への差し戻し")

  /** 予約をキャンセルする（US13 / `CancelBookingCommand`）。キャンセル成功時は確認通知を記録する。 */
  def cancel(bookingId: String): Either[String, Cargo] =
    transition(bookingId, _.cancel(), "キャンセル").map { cargo =>
      logNotification(
        cargo,
        NotificationType.BookingCancelled,
        NotificationPayload.BookingCancelled(cargo.bookingId.value)
      )
      cargo
    }

  /** 荷役作業記録通知を Booking 側で記録する（US15）。Cargo の状態遷移は行わない（状態は Tracking 側 currentStatus で表現）。
    *
    * Controller 層から HandlingCommandService 実行後に呼び出される。
    */
  def logHandlingNotification(
      bookingId: String,
      trackingNumber: String,
      eventType: String,
      location: String
  ): Either[String, Unit] =
    BookingId(bookingId).left
      .map(_ => CargoErrorMessages.invalidBookingIdMessage(bookingId))
      .flatMap { id =>
        repository.findById(id).toRight(CargoErrorMessages.bookingNotFoundMessage(bookingId)).map { cargo =>
          logNotification(
            cargo,
            NotificationType.HandlingRecorded,
            NotificationPayload.HandlingRecorded(cargo.bookingId.value, trackingNumber, eventType, location)
          )
        }
      }

  /** 追跡番号を発行する（US14 / `AssignTrackingNumberCommand`）。
    *
    *   - `Confirmed` 以外からは状態遷移違反
    *   - 既発行は冪等成功（既存番号を返す）
    *   - 成功時は `TrackingIssued` 状態に遷移し、`NotificationType.TrackingIssued` ログを記録
    */
  def issueTracking(bookingId: String, trackingNumber: String): Either[String, Cargo] =
    transition(bookingId, _.issueTrackingByRaw(trackingNumber), "追跡番号発行").map { cargo =>
      logNotification(
        cargo,
        NotificationType.TrackingIssued,
        NotificationPayload.TrackingIssued(cargo.bookingId.value, trackingNumber)
      )
      cargo
    }

  /** 引取完了で Cargo を Delivered 遷移 + 配送完了通知を記録（US16 / IT6）。 */
  def completeDelivery(
      bookingId: String,
      trackingNumber: String,
      location: String,
      recipientConfirmation: String
  ): Either[String, Cargo] =
    transition(bookingId, _.deliver(), "引取完了").map { cargo =>
      logNotification(
        cargo,
        NotificationType.DeliveryCompleted,
        NotificationPayload.DeliveryCompleted(
          cargo.bookingId.value,
          trackingNumber,
          location,
          recipientConfirmation
        )
      )
      cargo
    }

  private def logNotification(
      cargo: Cargo,
      notificationType: NotificationType,
      payload: NotificationPayload
  ): Unit =
    cargotracker.booking.domain.model.aggregates.NotificationLog
      .create(cargo.bookingId, notificationType, clock.instant(), NotificationPayloadJson.asString(payload))
      .foreach(notificationRepository.save)

  private def transition(
      bookingId: String,
      op: Cargo => Either[Cargo.Error, Cargo],
      action: String
  ): Either[String, Cargo] =
    for
      id <- BookingId(bookingId).left.map(_ => CargoErrorMessages.invalidBookingIdMessage(bookingId))
      cargo <- repository.findById(id).toRight(CargoErrorMessages.bookingNotFoundMessage(bookingId))
      next <- op(cargo).left.map(
        CargoErrorMessages.toMessage(_, CargoErrorMessages.actionFailureFallback(bookingId, action))
      )
    yield
      repository.save(next)
      next

  /** 経路情報を予約に紐付ける（US11 / `AssignItineraryCommand`）。
    *
    *   - 予約が存在しない場合は `Left("予約 BK-... が見つかりません")`
    *   - 状態遷移違反は `Left("現在の状態 ... から RouteAssigned への遷移はできません")`
    *   - 成功時は `RouteAssigned` 状態かつ `itinerary` を保持した Cargo を保存して返す
    *
    * Routing Context の `RoutingCommandService.confirmRoute` から US09 完了直後に 同一トランザクションで呼び出される（IT4 タスク 2.3）。
    */
  def assignItinerary(bookingId: String, voyageNumbers: List[String]): Either[String, Cargo] =
    for
      itinerary <- Itinerary(voyageNumbers).left.map(_ => "経路に含む航海が指定されていません")
      next <- transition(bookingId, _.assignItinerary(itinerary), "経路紐付け")
    yield next

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
