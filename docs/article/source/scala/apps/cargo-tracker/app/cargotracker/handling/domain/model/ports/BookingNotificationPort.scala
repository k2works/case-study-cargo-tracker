package cargotracker.handling.domain.model.ports

/** Handling Context が Booking に対して発火する通知・状態遷移の出力ポート（IT7 0.3）。 */
trait BookingNotificationPort:
  /** 荷役発生通知を Booking 側の通知ログに追記する。 */
  def logHandling(
      bookingId: String,
      trackingNumber: String,
      eventType: String,
      locationUnLocode: String
  ): Either[String, Unit]

  /** 引取完了 (Claim) で Booking 側の Cargo を Delivered 遷移 + 配送完了通知を記録する。 */
  def completeDelivery(
      bookingId: String,
      trackingNumber: String,
      locationUnLocode: String,
      recipientConfirmation: String
  ): Either[String, Unit]
