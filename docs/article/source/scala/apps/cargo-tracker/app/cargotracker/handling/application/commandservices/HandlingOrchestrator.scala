package cargotracker.handling.application.commandservices

import cargotracker.handling.domain.model.ports.{BookingNotificationPort, HandlingCargoQueryPort, TrackingLookupPort}
import cargotracker.shared.application.TransactionBoundary

import java.time.Instant
import javax.inject.{Inject, Singleton}

/** 荷役登録から Tracking event 追記・Booking 通知・Claim 時の completeDelivery までを一括実行する アプリケーションサービス（IT7 0.3 / IT5 申し送り 0.3 / IT8
  * 0.11 routeDeviation 自動判定 + HandlingCargoQueryPort 導入 / IT9 0.1 ADR 0016 案 A 単一 TX 化 Phase 1）。
  *
  *   - Tracking / Booking との連携は出力ポート経由で行い、Handling Context の application 層は他コンテキスト の domain / application /
  *     infrastructure に直接依存しない（ヘキサゴナル境界）
  *   - **IT9 0.1 (ADR 0016 案 A Phase 1)**: HandlingOrchestrator が `DB.localTx { implicit session => ... }` で明示的に TX
  *     境界を開く。 Handling 部分 (handlingCommandService.register) は同 session で実行され、失敗時に rollback される。 Tracking/Booking 部分は
  *     Phase 2 で各 Repository を implicit DBSession 受取版に拡張し統合予定 (IT9 後半 or IT10)
  *   - `routeDeviation` は `HandlingCargoQueryPort.isOnRoute` で自動判定
  */
@Singleton
class HandlingOrchestrator @Inject() (
    handlingCommandService: HandlingCommandService,
    trackingPort: TrackingLookupPort,
    bookingPort: BookingNotificationPort,
    cargoQueryPort: HandlingCargoQueryPort,
    txBoundary: TransactionBoundary
):

  /** 荷役登録の一括フロー実行。
    *
    * IT9 0.1 (ADR 0016 案 A Phase 1): `DB.localTx` で TX 境界を明示。Handling 部分は同 session で実行される。 ロジック失敗時 (Left 返却) は
    * `HandlingOrchestratorRollback` を throw して localTx を rollback する。
    */
  def register(input: RegisterHandlingFlowInput): Either[String, Unit] =
    try
      txBoundary.inLocalTx { session =>
        given scalikejdbc.DBSession = session
        val result: Either[String, Unit] = for
          bookingId <- trackingPort
            .findBookingIdByTrackingNumber(input.trackingNumber)
            .toRight(s"追跡番号 ${input.trackingNumber} が見つかりません")
          routeDeviation = !cargoQueryPort.isOnRoute(bookingId, input.locationUnLocode)
          _ <- handlingCommandService
            .registerInTx(
              RegisterHandlingActivityCommand(
                bookingId = bookingId,
                eventType = input.eventType,
                completionTime = input.completionTime,
                locationUnLocode = input.locationUnLocode,
                voyageNumber = input.voyageNumber,
                operatorName = input.operatorName,
                routeDeviation = routeDeviation,
                recipientConfirmation = input.recipientConfirmation,
                recipientConfirmationType = input.recipientConfirmationType
              )
            )(session) // IT9 0.1: implicit session で外側 TX に参加
            .map(_ => ())
          _ <- trackingPort.recordEvent(
            trackingNumber = input.trackingNumber,
            eventType = input.eventType,
            eventTime = input.completionTime,
            locationUnLocode = input.locationUnLocode,
            voyageNumber = input.voyageNumber,
            routeDeviation = routeDeviation
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
        // Left 返却時は rollback のため例外を throw
        result.left.foreach(msg => throw HandlingOrchestrator.HandlingOrchestratorRollback(msg))
        result
      }
    catch case HandlingOrchestrator.HandlingOrchestratorRollback(msg) => Left(msg)

object HandlingOrchestrator:
  /** IT9 0.1 (ADR 0016 案 A Phase 1): DB.localTx を rollback させるためのマーカー例外 (内部実装、外部公開しない)。 */
  private[commandservices] final case class HandlingOrchestratorRollback(message: String)
      extends RuntimeException(message)

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
