package cargotracker.booking.infrastructure.repositories

import cargotracker.booking.domain.model.aggregates.NotificationLog
import cargotracker.booking.domain.model.valueobjects.{BookingId, NotificationType}
import cargotracker.support.PostgresContainerSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers.*

import java.time.Instant

/** ScalikeJDBC 実装 NotificationLog リポジトリの永続化往復テスト（IT4 タスク 3.5）。 */
class ScalikeJdbcNotificationLogRepositorySpec extends AnyWordSpec with Matchers with PostgresContainerSupport:

  "ScalikeJdbcNotificationLogRepository" should {

    "save した通知ログを findByBookingId で sent_at 降順に取得できる" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val repo = app.injector.instanceOf[cargotracker.booking.domain.model.repositories.NotificationLogRepository]
        val bookingId = BookingId.unsafeFrom("BK-000123")

        val older = NotificationLog
          .create(bookingId, NotificationType.RouteNotified, Instant.parse("2026-06-21T10:00:00Z"), """{"a":1}""")
          .toOption
          .get
        val newer = NotificationLog
          .create(bookingId, NotificationType.BookingConfirmed, Instant.parse("2026-06-21T12:00:00Z"), """{"b":2}""")
          .toOption
          .get
        repo.save(older)
        repo.save(newer)

        val results = repo.findByBookingId(bookingId)
        results.map(_.notificationType) shouldBe Seq(
          NotificationType.BookingConfirmed,
          NotificationType.RouteNotified
        )
        results.map(_.payload) shouldBe Seq("""{"b":2}""", """{"a":1}""")
      }
    }

    "別の予約 ID の通知ログは混入しない" in withContainers { container =>
      val app = buildApp(container)
      running(app) {
        val repo = app.injector.instanceOf[cargotracker.booking.domain.model.repositories.NotificationLogRepository]
        val a = BookingId.unsafeFrom("BK-000A01")
        val b = BookingId.unsafeFrom("BK-000B01")

        repo.save(
          NotificationLog
            .create(a, NotificationType.RouteNotified, Instant.parse("2026-06-21T10:00:00Z"), "{}")
            .toOption
            .get
        )
        repo.save(
          NotificationLog
            .create(b, NotificationType.RouteNotified, Instant.parse("2026-06-21T10:00:00Z"), "{}")
            .toOption
            .get
        )

        repo.findByBookingId(a) should have size 1
      }
    }
  }
