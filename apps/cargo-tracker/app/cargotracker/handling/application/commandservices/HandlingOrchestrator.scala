package cargotracker.handling.application.commandservices

import cargotracker.handling.domain.model.ports.{BookingNotificationPort, TrackingLookupPort}

import java.time.Instant
import javax.inject.{Inject, Singleton}

/** 荷役登録から Tracking event 追記・Booking 通知・Claim 時の completeDelivery までを一括実行する アプリケーションサービス（IT7 0.3 / IT5 申し送り 0.3）。
  *
  *   - Tracking / Booking との連携は出力ポート経由で行い、Handling Context の application 層は他コンテキスト の domain / application /
  *     infrastructure に直接依存しない（ヘキサゴナル境界）
  *   - 単一トランザクション境界は将来課題（各リポジトリが独立 `DB.localTx` を開く現状制約）。本コミットでは orchestration ロジックの集約とテスト容易性の確保を優先する
  */
@Singleton
class HandlingOrchestrator @Inject() (
    handlingCommandService: HandlingCommandService,
    trackingPort: TrackingLookupPort,
    bookingPort: BookingNotificationPort
):

  /** 荷役登録の一括フロー実行。 */
  def register(input: RegisterHandlingFlowInput): Either[String, Unit] =
    for
      bookingId <- trackingPort
        .findBookingIdByTrackingNumber(input.trackingNumber)
        .toRight(s"追跡番号 ${input.trackingNumber} が見つかりません")
      _ <- handlingCommandService
        .register(
          RegisterHandlingActivityCommand(
            bookingId = bookingId,
            eventType = input.eventType,
            completionTime = input.completionTime,
            locationUnLocode = input.locationUnLocode,
            voyageNumber = input.voyageNumber,
            operatorName = input.operatorName,
            routeDeviation = false,
            recipientConfirmation = input.recipientConfirmation,
            recipientConfirmationType = input.recipientConfirmationType
          )
        )
        .map(_ => ())
      _ <- trackingPort.recordEvent(
        trackingNumber = input.trackingNumber,
        eventType = input.eventType,
        eventTime = input.completionTime,
        locationUnLocode = input.locationUnLocode,
        voyageNumber = input.voyageNumber,
        routeDeviation = false
      )
      _ <- bookingPort.logHandling(bookingId, input.trackingNumber, input.eventType, input.locationUnLocode)
      _ <-
        if input.eventType == "Claim" then
          bookingPort.completeDelivery(
            bookingId,
            input.trackingNumber,
            input.locationUnLocode,
            input.recipientConfirmation.getOrElse("")
          )
        else Right(())
    yield ()

/** 荷役登録フローへの入力データ。 */
final case class RegisterHandlingFlowInput(
    trackingNumber: String,
    eventType: String,
    completionTime: Instant,
    locationUnLocode: String,
    voyageNumber: Option[String] = None,
    operatorName: Option[String] = None,
    recipientConfirmation: Option[String] = None,
    recipientConfirmationType: Option[String] = None
)
