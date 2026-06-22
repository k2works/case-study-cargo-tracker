package cargotracker.booking.application.notifications

import cargotracker.booking.domain.model.valueobjects.NotificationPayload
import play.api.libs.json.{JsValue, Json}

/** [[NotificationPayload]] の JSON シリアライズ責務を application 層に隔離する。
  *
  * ドメイン層に Play JSON 依存を持ち込まないため、本オブジェクトで集約する（ヘキサゴナル違反回避）。 IT4 セルフレビュー H1 対応。
  */
object NotificationPayloadJson:

  def toJson(payload: NotificationPayload): JsValue = payload match
    case p: NotificationPayload.RouteNotified =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "origin" -> p.origin,
        "destination" -> p.destination,
        "arrivalDeadline" -> p.arrivalDeadline.toString,
        "voyages" -> p.voyages
      )
    case p: NotificationPayload.BookingConfirmed =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "status" -> "Confirmed",
        "trackingIssueRequested" -> p.trackingIssueRequested
      )
    case p: NotificationPayload.BookingCancelled =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "status" -> "Cancelled"
      )

  def asString(payload: NotificationPayload): String = Json.stringify(toJson(payload))
