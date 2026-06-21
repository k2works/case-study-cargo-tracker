package cargotracker.booking.domain.model.aggregates

import cargotracker.booking.domain.model.valueobjects.{BookingId, NotificationType}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class NotificationLogSpec extends AnyFunSuite with Matchers:

  private val bookingId = BookingId.unsafeFrom("BK-000001")
  private val now = Instant.parse("2026-06-21T10:00:00Z")

  test("create: payload 付きで version 0 のログを生成する"):
    val Right(log) = NotificationLog.create(
      bookingId,
      NotificationType.RouteNotified,
      now,
      """{"voyages":["VY-1"]}"""
    ): @unchecked
    log.notificationType shouldBe NotificationType.RouteNotified
    log.payload should include("VY-1")
    log.version shouldBe 0

  test("create: 空 payload は EmptyPayload"):
    NotificationLog.create(bookingId, NotificationType.RouteNotified, now, "") shouldBe
      Left(NotificationLog.EmptyPayload)

  test("reconstruct: 永続化からの復元"):
    val log = NotificationLog.reconstruct(bookingId, NotificationType.BookingConfirmed, now, "{}", version = 2)
    log.version shouldBe 2
    log.notificationType shouldBe NotificationType.BookingConfirmed

  test("NotificationType.fromName: 既知の名称をパースする"):
    NotificationType.fromName("RouteNotified") shouldBe Some(NotificationType.RouteNotified)
    NotificationType.fromName("BookingConfirmed") shouldBe Some(NotificationType.BookingConfirmed)
    NotificationType.fromName("BookingCancelled") shouldBe Some(NotificationType.BookingCancelled)
    NotificationType.fromName("Unknown") shouldBe None
