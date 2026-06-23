package cargotracker.booking.domain.model.valueobjects

/** 通知ログの種別（US12 / US13 / US14）。 */
enum NotificationType:
  case RouteNotified // 経路通知（US12）
  case BookingConfirmed // 予約確定通知（US13）
  case BookingCancelled // 予約キャンセル通知（US13）
  case TrackingIssued // 追跡番号発行通知（US14）
  case HandlingRecorded // 荷役作業記録通知（US15）
  case DeliveryCompleted // 配送完了通知（US16 / IT6、社内視点）。UI / 荷主向けは「引取済」と表現する（IT7 0.15 ユビキタス言語統一）
  case ManualStatusUpdated // 手動状態更新通知（US17 / IT6）
  case DelayNotified // 遅延通知（US19 / IT7）
  case DamageReported // 破損通知（US20 / IT7）
  case LossEscalated // 紛失 + 管理職エスカレーション通知（US20 / IT7）
  case ExceptionResponded // 例外対応報告通知（US19 / US20 / IT7）

object NotificationType:
  def fromName(name: String): Option[NotificationType] =
    values.find(_.toString == name)
