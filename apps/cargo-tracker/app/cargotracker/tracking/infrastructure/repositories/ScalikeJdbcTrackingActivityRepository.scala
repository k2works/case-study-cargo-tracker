package cargotracker.tracking.infrastructure.repositories

import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.entities.TrackingActivityEvent
import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingLocation, TrackingNumber}
import scalikejdbc.*

import javax.inject.Singleton

@Singleton
class ScalikeJdbcTrackingActivityRepository extends TrackingActivityRepository:

  private def rowToActivity(rs: WrappedResultSet): Option[(Long, TrackingActivity)] =
    for
      tn <- TrackingNumber(rs.string("tracking_number")).toOption
      bid <- TrackingBookingId(rs.string("booking_id")).toOption
      status = TrackingStatus.values
        .find(_.toString == rs.string("transport_status"))
        .getOrElse(TrackingStatus.Unknown)
    yield (rs.long("id"), TrackingActivity.reconstruct(tn, bid, status, version = rs.int("version")))

  private def loadEvents(trackingId: Long)(implicit session: DBSession): List[TrackingActivityEvent] =
    sql"""SELECT * FROM tracking_handling_event
          WHERE tracking_id = $trackingId
          ORDER BY event_time"""
      .map { rs =>
        TrackingActivityEvent(
          eventType = rs.string("event_type"),
          eventTime = rs.timestamp("event_time").toInstant,
          location = TrackingLocation.of(rs.string("location_unlocode")),
          voyageNumber = rs.stringOpt("voyage_number"),
          routeDeviation = rs.boolean("route_deviation")
        )
      }
      .list
      .apply()

  private def attachEvents(idAndActivity: (Long, TrackingActivity))(implicit
      session: DBSession
  ): TrackingActivity =
    val (id, base) = idAndActivity
    val events = loadEvents(id)
    TrackingActivity.reconstruct(
      base.trackingNumber,
      base.bookingId,
      base.transportStatus,
      events,
      base.version
    )

  override def nextTrackingNumber(): TrackingNumber =
    DB.readOnly { implicit session =>
      val next = sql"""
        SELECT COALESCE(MAX(id), 0) + 1 AS next FROM tracking_activity
      """.map(_.long("next")).single.apply().getOrElse(1L)
      TrackingNumber.fromSequence(next)
    }

  override def findByTrackingNumber(tn: TrackingNumber): Option[TrackingActivity] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM tracking_activity WHERE tracking_number = ${tn.value}"
        .map(rowToActivity)
        .single
        .apply()
        .flatten
        .map(attachEvents)
    }

  override def findByBookingId(bid: TrackingBookingId): Option[TrackingActivity] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM tracking_activity WHERE booking_id = ${bid.value}"
        .map(rowToActivity)
        .single
        .apply()
        .flatten
        .map(attachEvents)
    }

  override def save(activity: TrackingActivity): Unit =
    DB.localTx { implicit session =>
      val existing =
        sql"SELECT id FROM tracking_activity WHERE tracking_number = ${activity.trackingNumber.value}"
          .map(_.long("id"))
          .single
          .apply()

      val trackingId = existing match
        case Some(id) =>
          val updated = sql"""
            UPDATE tracking_activity
            SET transport_status = ${activity.transportStatus.toString},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE tracking_number = ${activity.trackingNumber.value} AND version = ${activity.version}
          """.update.apply()
          if updated == 0 then
            throw cargotracker.shared.domain.OptimisticLockException(
              entityType = "TrackingActivity",
              identifier = activity.trackingNumber.value
            )
          id
        case None =>
          sql"""
            INSERT INTO tracking_activity
              (tracking_number, booking_id, transport_status)
            VALUES
              (${activity.trackingNumber.value},
               ${activity.bookingId.value},
               ${activity.transportStatus.toString})
          """.updateAndReturnGeneratedKey.apply()

      // save() ではイベント差分書込はしない（appendEvent 経由）
    }

  override def appendEvent(activity: TrackingActivity, newEvent: TrackingActivityEvent): Unit =
    DB.localTx { implicit session =>
      val trackingId = sql"SELECT id FROM tracking_activity WHERE tracking_number = ${activity.trackingNumber.value}"
        .map(_.long("id"))
        .single
        .apply()
        .getOrElse(
          throw IllegalStateException(
            s"tracking_activity not found for ${activity.trackingNumber.value}（save 未実行）"
          )
        )

      val updated = sql"""
        UPDATE tracking_activity
        SET transport_status = ${activity.transportStatus.toString},
            version = version + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE tracking_number = ${activity.trackingNumber.value} AND version = ${activity.version}
      """.update.apply()
      if updated == 0 then
        throw cargotracker.shared.domain.OptimisticLockException(
          entityType = "TrackingActivity",
          identifier = activity.trackingNumber.value
        )

      sql"""
        INSERT INTO tracking_handling_event
          (tracking_id, event_type, event_time, location_unlocode, voyage_number, route_deviation)
        VALUES
          ($trackingId, ${newEvent.eventType},
           ${java.sql.Timestamp.from(newEvent.eventTime)},
           ${newEvent.location.unLocode},
           ${newEvent.voyageNumber.orNull},
           ${newEvent.routeDeviation})
      """.update.apply()
    }
