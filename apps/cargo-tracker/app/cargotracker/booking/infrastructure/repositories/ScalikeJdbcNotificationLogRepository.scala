package cargotracker.booking.infrastructure.repositories

import cargotracker.booking.domain.model.aggregates.NotificationLog
import cargotracker.booking.domain.model.repositories.NotificationLogRepository
import cargotracker.booking.domain.model.valueobjects.{BookingId, NotificationType}
import scalikejdbc.*

import javax.inject.Singleton

/** ScalikeJDBC 実装の通知ログリポジトリ（US12 / US13 / V11）。
  *
  * 1 予約に複数通知を時系列降順で取得する。
  */
@Singleton
class ScalikeJdbcNotificationLogRepository extends NotificationLogRepository:

  override def findByBookingId(bookingId: BookingId): Seq[NotificationLog] =
    DB.readOnly { implicit session =>
      sql"""SELECT booking_id, type, sent_at, payload, version
            FROM notification_log
            WHERE booking_id = ${bookingId.value}
            ORDER BY sent_at DESC"""
        .map { rs =>
          val nt = NotificationType.fromName(rs.string("type")).getOrElse(NotificationType.RouteNotified)
          NotificationLog.reconstruct(
            bookingId = BookingId.unsafeFrom(rs.string("booking_id")),
            notificationType = nt,
            sentAt = rs.zonedDateTime("sent_at").toInstant,
            payload = rs.string("payload"),
            version = rs.int("version")
          )
        }
        .list
        .apply()
    }

  override def save(log: NotificationLog): Unit =
    DB.localTx { implicit session =>
      sql"""INSERT INTO notification_log
              (booking_id, type, sent_at, payload, version)
            VALUES
              (${log.bookingId.value},
               ${log.notificationType.toString},
               ${java.sql.Timestamp.from(log.sentAt)},
               ${log.payload},
               ${log.version})""".update.apply()
    }
