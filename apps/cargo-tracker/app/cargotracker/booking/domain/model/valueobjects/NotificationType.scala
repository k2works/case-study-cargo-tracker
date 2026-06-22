package cargotracker.booking.domain.model.valueobjects

/** 通知ログの種別（US12 / US13 / US14）。 */
enum NotificationType:
  case RouteNotified // 経路通知（US12）
  case BookingConfirmed // 予約確定通知（US13）
  case BookingCancelled // 予約キャンセル通知（US13）
  case TrackingIssued // 追跡番号発行通知（US14）
  case HandlingRecorded // 荷役作業記録通知（US15）

object NotificationType:
  def fromName(name: String): Option[NotificationType] =
    values.find(_.toString == name)
