package cargotracker.booking.domain.model.valueobjects

/** 通知ログの種別（US12 / US13）。 */
enum NotificationType:
  case RouteNotified // 経路通知（US12）
  case BookingConfirmed // 予約確定通知（US13）
  case BookingCancelled // 予約キャンセル通知（US13）

object NotificationType:
  def fromName(name: String): Option[NotificationType] =
    values.find(_.toString == name)
