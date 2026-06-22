package cargotracker.booking.application.commandservices

import cargotracker.booking.application.notifications.NotificationPayloadJson
import cargotracker.booking.domain.model.aggregates.NotificationLog
import cargotracker.booking.domain.model.repositories.{CargoRepository, NotificationLogRepository}
import cargotracker.booking.domain.model.valueobjects.{
  BookingId,
  BookingStatus,
  NotificationPayload,
  NotificationType,
  RouteSpecification
}

import java.time.Clock
import javax.inject.{Inject, Singleton}

/** 経路通知コマンドサービス（US12）。
  *
  *   - 経路紐付け済み（`RouteAssigned`）の予約に対してのみ通知発行を許可する
  *   - ペイロードは経路（航海番号列）+ 出発地 / 目的地 / 希望着日 を JSON 風に文字列化
  *   - メール送信は IT5+。本サービスは DB ログのみ
  */
@Singleton
class NotifyRouteCommandService @Inject() (
    cargoRepository: CargoRepository,
    notificationRepository: NotificationLogRepository,
    clock: Clock
):

  def notify(bookingId: String): Either[String, NotificationLog] =
    for
      id <- BookingId(bookingId).left.map(_ => s"予約 ID の形式が不正です: $bookingId")
      cargo <- cargoRepository.findById(id).toRight(s"予約 $bookingId が見つかりません")
      _ <- Either.cond(
        cargo.status == BookingStatus.RouteAssigned,
        (),
        s"予約 $bookingId は経路未紐付けのため通知できません（現在の状態: ${cargo.status}）"
      )
      itinerary <- cargo.itinerary.toRight(s"予約 $bookingId に紐付け経路がありません")
      payload = buildPayload(cargo.bookingId.value, itinerary.voyageNumbers, cargo.routeSpecification)
      log <- NotificationLog
        .create(id, NotificationType.RouteNotified, clock.instant(), NotificationPayloadJson.asString(payload))
        .left
        .map(_ => "通知ペイロードの生成に失敗しました")
    yield
      notificationRepository.save(log)
      log

  private def buildPayload(
      bookingIdValue: String,
      voyageNumbers: List[String],
      routeSpec: RouteSpecification
  ): NotificationPayload.RouteNotified =
    NotificationPayload.RouteNotified(
      bookingId = bookingIdValue,
      origin = routeSpec.origin.unLocode,
      destination = routeSpec.destination.unLocode,
      arrivalDeadline = routeSpec.arrivalDeadline,
      voyages = voyageNumbers
    )
