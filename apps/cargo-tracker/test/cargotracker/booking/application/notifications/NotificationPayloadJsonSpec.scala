package cargotracker.booking.application.notifications

import cargotracker.booking.domain.model.valueobjects.NotificationPayload
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import java.time.LocalDate

class NotificationPayloadJsonSpec extends AnyWordSpec with Matchers:

  "NotificationPayloadJson" should:
    "RouteNotified を bookingId / origin / destination / arrivalDeadline / voyages の 5 キーで JSON 化する" in:
      val payload = NotificationPayload.RouteNotified(
        bookingId = "BK-0001",
        origin = "JPTYO",
        destination = "USLAX",
        arrivalDeadline = LocalDate.of(2099, 12, 31),
        voyages = List("VY-001", "VY-002")
      )
      val json = Json.parse(NotificationPayloadJson.asString(payload))

      (json \ "bookingId").as[String] shouldBe "BK-0001"
      (json \ "origin").as[String] shouldBe "JPTYO"
      (json \ "destination").as[String] shouldBe "USLAX"
      (json \ "arrivalDeadline").as[String] shouldBe "2099-12-31"
      (json \ "voyages").as[List[String]] shouldBe List("VY-001", "VY-002")

    "RouteNotified の voyages が空でも有効な JSON を生成する" in:
      val payload = NotificationPayload.RouteNotified(
        bookingId = "BK-0002",
        origin = "JPTYO",
        destination = "USLAX",
        arrivalDeadline = LocalDate.of(2099, 1, 1),
        voyages = Nil
      )
      val json = Json.parse(NotificationPayloadJson.asString(payload))

      (json \ "voyages").as[List[String]] shouldBe Nil

    "特殊文字（クォート）を含むフィールドでもエスケープ漏れがない" in:
      val payload = NotificationPayload.RouteNotified(
        bookingId = """BK-"INJECT"""",
        origin = "JPTYO",
        destination = "USLAX",
        arrivalDeadline = LocalDate.of(2099, 6, 1),
        voyages = List("VY\"-001")
      )
      val json = Json.parse(NotificationPayloadJson.asString(payload))

      (json \ "bookingId").as[String] shouldBe """BK-"INJECT""""
      (json \ "voyages").as[List[String]] shouldBe List("VY\"-001")

    "BookingConfirmed は status=Confirmed と trackingIssueRequested=true をデフォルトで含む" in:
      val payload = NotificationPayload.BookingConfirmed("BK-0003")
      val json = Json.parse(NotificationPayloadJson.asString(payload))

      (json \ "bookingId").as[String] shouldBe "BK-0003"
      (json \ "status").as[String] shouldBe "Confirmed"
      (json \ "trackingIssueRequested").as[Boolean] shouldBe true

    "BookingConfirmed の trackingIssueRequested を false に切り替えられる" in:
      val payload = NotificationPayload.BookingConfirmed("BK-0004", trackingIssueRequested = false)
      val json = Json.parse(NotificationPayloadJson.asString(payload))

      (json \ "trackingIssueRequested").as[Boolean] shouldBe false

    "BookingCancelled は status=Cancelled を含む" in:
      val payload = NotificationPayload.BookingCancelled("BK-0005")
      val json = Json.parse(NotificationPayloadJson.asString(payload))

      (json \ "bookingId").as[String] shouldBe "BK-0005"
      (json \ "status").as[String] shouldBe "Cancelled"
