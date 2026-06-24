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
    case p: NotificationPayload.TrackingIssued =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "trackingNumber" -> p.trackingNumber,
        "status" -> "TrackingIssued",
        "trackingUrl" -> s"/public/tracking/${p.trackingNumber}"
      )
    case p: NotificationPayload.HandlingRecorded =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "trackingNumber" -> p.trackingNumber,
        "eventType" -> p.eventType,
        "location" -> p.location
      )
    case p: NotificationPayload.DeliveryCompleted =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "trackingNumber" -> p.trackingNumber,
        "status" -> "Delivered",
        "location" -> p.location,
        "recipientConfirmation" -> p.recipientConfirmation
      )
    case p: NotificationPayload.ManualStatusUpdated =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "trackingNumber" -> p.trackingNumber,
        "status" -> p.status,
        "location" -> p.location,
        "reason" -> p.reason
      )
    case p: NotificationPayload.DelayNotified =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "trackingNumber" -> p.trackingNumber,
        "newEstimatedArrival" -> p.newEstimatedArrival,
        "responsePlan" -> p.responsePlan,
        "reason" -> p.reason
      )
    case p: NotificationPayload.DamageReported =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "trackingNumber" -> p.trackingNumber,
        "location" -> p.location,
        "description" -> p.description
      )
    case p: NotificationPayload.LostEscalated =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "trackingNumber" -> p.trackingNumber,
        "location" -> p.location,
        "description" -> p.description,
        "escalation" -> true
      )
    case p: NotificationPayload.ExceptionResponded =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "trackingNumber" -> p.trackingNumber,
        "exceptionType" -> p.exceptionType,
        "resolutionNotes" -> p.resolutionNotes
      )
    case p: NotificationPayload.PaymentRequested =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "invoiceNumber" -> p.invoiceNumber,
        "dueDate" -> p.dueDate,
        "paymentReference" -> p.paymentReference,
        "amount" -> p.amount
      )
    case p: NotificationPayload.PaymentConfirmed =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "invoiceNumber" -> p.invoiceNumber,
        "paidAt" -> p.paidAt,
        "amount" -> p.amount
      )
    case p: NotificationPayload.OverdueAlerted =>
      Json.obj(
        "bookingId" -> p.bookingId,
        "invoiceNumber" -> p.invoiceNumber,
        "dueDate" -> p.dueDate,
        "amount" -> p.amount
      )

  def asString(payload: NotificationPayload): String = Json.stringify(toJson(payload))
