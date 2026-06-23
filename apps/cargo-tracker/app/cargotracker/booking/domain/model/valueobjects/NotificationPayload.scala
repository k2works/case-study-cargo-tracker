package cargotracker.booking.domain.model.valueobjects

import java.time.LocalDate

/** 通知ペイロード（US12 / US13）。
  *
  *   - 経路通知 / 予約確定 / 予約キャンセルの 3 種を ADT で型安全に表現する
  *   - `BookingCommandService` / `NotifyRouteCommandService` の文字列ハードコーディングを一本化
  *   - JSON 直書き（エスケープ漏れ・フィールド名タイポ）を防ぐ
  *   - JSON 化は application 層の [[cargotracker.booking.application.notifications.NotificationPayloadJson]] が担う
  *   - IT4 セルフレビュー H1 対応
  */
sealed trait NotificationPayload:
  def bookingId: String

object NotificationPayload:

  /** 経路通知ペイロード（US12 / `NotifyRouteCommandService`）。 */
  final case class RouteNotified(
      bookingId: String,
      origin: String,
      destination: String,
      arrivalDeadline: LocalDate,
      voyages: List[String]
  ) extends NotificationPayload

  /** 予約確定通知ペイロード（US13 / `BookingCommandService.confirm`）。 */
  final case class BookingConfirmed(
      bookingId: String,
      trackingIssueRequested: Boolean = true
  ) extends NotificationPayload

  /** 予約キャンセル通知ペイロード（US13 / `BookingCommandService.cancel`）。 */
  final case class BookingCancelled(bookingId: String) extends NotificationPayload

  /** 追跡番号発行通知ペイロード（US14 / `BookingCommandService.issueTracking`）。 */
  final case class TrackingIssued(
      bookingId: String,
      trackingNumber: String
  ) extends NotificationPayload

  /** 荷役記録通知ペイロード（US15 / `HandlingController` 経由）。 */
  final case class HandlingRecorded(
      bookingId: String,
      trackingNumber: String,
      eventType: String,
      location: String
  ) extends NotificationPayload

  /** 配送完了通知ペイロード（US16 / `BookingCommandService.completeDelivery`）。 */
  final case class DeliveryCompleted(
      bookingId: String,
      trackingNumber: String,
      location: String,
      recipientConfirmation: String
  ) extends NotificationPayload

  /** 手動状態更新通知ペイロード（US17 / `TrackingCommandService.updateStatus`）。 */
  final case class ManualStatusUpdated(
      bookingId: String,
      trackingNumber: String,
      status: String,
      location: String
  ) extends NotificationPayload
