package cargotracker.tracking.infrastructure.repositories

import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.entities.{TrackingActivityEvent, TrackingExceptionEvent}
import cargotracker.tracking.domain.model.enums.{ExceptionType, TrackingStatus}
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.{
  TrackingBookingId,
  TrackingExceptionEventId,
  TrackingLocation,
  TrackingNumber
}
import scalikejdbc.*

import java.time.Instant
import javax.inject.Singleton

@Singleton
class ScalikeJdbcTrackingActivityRepository extends TrackingActivityRepository:

  private def rowToBase(rs: WrappedResultSet): Option[(Long, TrackingNumber, TrackingBookingId, TrackingStatus, Int)] =
    for
      tn <- TrackingNumber(rs.string("tracking_number")).toOption
      bid <- TrackingBookingId(rs.string("booking_id")).toOption
      status = TrackingStatus.values
        .find(_.toString == rs.string("transport_status"))
        .getOrElse(TrackingStatus.Unknown)
    yield (rs.long("id"), tn, bid, status, rs.int("version"))

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

  private def loadExceptions(trackingId: Long)(implicit session: DBSession): List[TrackingExceptionEvent] =
    sql"""SELECT id, exception_type, location_unlocode, occurred_at,
                 description, escalation_flag, resolved_at, resolution_notes
          FROM tracking_exception_event
          WHERE tracking_id = $trackingId
          ORDER BY id"""
      .map { rs =>
        val cat = ExceptionType.fromName(rs.string("exception_type")).getOrElse(ExceptionType.Delay)
        TrackingExceptionEvent(
          exceptionType = cat,
          location = TrackingLocation.of(rs.string("location_unlocode")),
          occurredAt = rs.timestamp("occurred_at").toInstant,
          description = rs.stringOpt("description"),
          escalationFlag = rs.boolean("escalation_flag"),
          resolvedAt = rs.zonedDateTimeOpt("resolved_at").map(_.toInstant),
          resolutionNotes = rs.stringOpt("resolution_notes"),
          id = Some(TrackingExceptionEventId(rs.long("id")))
        )
      }
      .list
      .apply()

  private def attachEvents(base: (Long, TrackingNumber, TrackingBookingId, TrackingStatus, Int))(implicit
      session: DBSession
  ): TrackingActivity =
    val (id, tn, bid, status, version) = base
    val events = loadEvents(id)
    val exceptions = loadExceptions(id)
    TrackingActivity.reconstruct(tn, bid, status, events, version, exceptions)

  override def nextTrackingNumber(): TrackingNumber =
    DB.readOnly { implicit session =>
      val next = sql"""
        SELECT nextval('tracking_activity_id_seq') AS next
      """.map(_.long("next")).single.apply().getOrElse(1L)
      TrackingNumber.fromSequence(next)
    }

  override def findByTrackingNumber(tn: TrackingNumber): Option[TrackingActivity] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM tracking_activity WHERE tracking_number = ${tn.value}"
        .map(rowToBase)
        .single
        .apply()
        .flatten
        .map(attachEvents)
    }

  override def findByBookingId(bid: TrackingBookingId): Option[TrackingActivity] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM tracking_activity WHERE booking_id = ${bid.value}"
        .map(rowToBase)
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

      existing match
        case Some(_) =>
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
        case None =>
          sql"""
            INSERT INTO tracking_activity
              (tracking_number, booking_id, transport_status)
            VALUES
              (${activity.trackingNumber.value},
               ${activity.bookingId.value},
               ${activity.transportStatus.toString})
          """.update.apply()
    }

  private def lockedTrackingId(activity: TrackingActivity)(implicit session: DBSession): Long =
    sql"SELECT id FROM tracking_activity WHERE tracking_number = ${activity.trackingNumber.value}"
      .map(_.long("id"))
      .single
      .apply()
      .getOrElse(
        throw IllegalStateException(
          s"tracking_activity not found for ${activity.trackingNumber.value}（save 未実行）"
        )
      )

  private def bumpVersion(activity: TrackingActivity)(implicit session: DBSession): Unit =
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

  override def appendEvent(activity: TrackingActivity, newEvent: TrackingActivityEvent): TrackingActivity =
    DB.localTx { implicit session =>
      val trackingId = lockedTrackingId(activity)
      bumpVersion(activity)
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

      TrackingActivity.reconstruct(
        trackingNumber = activity.trackingNumber,
        bookingId = activity.bookingId,
        transportStatus = activity.transportStatus,
        events = activity.events,
        version = activity.version + 1,
        exceptions = activity.exceptions
      )
    }

  override def appendException(
      activity: TrackingActivity,
      newException: TrackingExceptionEvent
  ): TrackingActivity =
    DB.localTx { implicit session =>
      val trackingId = lockedTrackingId(activity)
      bumpVersion(activity)
      val newId = sql"""
        INSERT INTO tracking_exception_event
          (tracking_id, exception_type, location_unlocode, occurred_at,
           description, escalation_flag, resolved_at, resolution_notes)
        VALUES
          ($trackingId,
           ${newException.exceptionType.toString},
           ${newException.location.unLocode},
           ${java.sql.Timestamp.from(newException.occurredAt)},
           ${newException.description.orNull},
           ${newException.escalationFlag},
           ${newException.resolvedAt.map(java.sql.Timestamp.from).orNull},
           ${newException.resolutionNotes.orNull})
      """.updateAndReturnGeneratedKey.apply()

      // IT8 0.5 (H5): 保存後の id を採番し、集約内の最終 exception に反映してから reconstruct
      val savedException = newException.copy(id = Some(TrackingExceptionEventId(newId)))
      val updatedExceptions =
        if activity.exceptions.lastOption.exists(e =>
            e.id.isEmpty && e.exceptionType == newException.exceptionType && e.occurredAt == newException.occurredAt
          )
        then activity.exceptions.init :+ savedException
        else activity.exceptions :+ savedException
      TrackingActivity.reconstruct(
        trackingNumber = activity.trackingNumber,
        bookingId = activity.bookingId,
        transportStatus = activity.transportStatus,
        events = activity.events,
        version = activity.version + 1,
        exceptions = updatedExceptions
      )
    }

  override def updateExceptionResolution(
      activity: TrackingActivity,
      index: Int,
      resolvedAt: Instant,
      resolutionNotes: String
  ): TrackingActivity =
    DB.localTx { implicit session =>
      val trackingId = lockedTrackingId(activity)
      bumpVersion(activity)
      val target = activity.exceptions(index)
      // IT8 0.5 (H5): id 直接更新に切り替え。id が None なら不整合 (未保存 example が UPDATE 対象に来ている)
      val exceptionId = target.id.getOrElse(
        throw IllegalStateException(
          s"TrackingExceptionEvent (tracking_id=$trackingId, index=$index) に id が設定されていません。保存後の集約を再ロードしてから呼び出してください"
        )
      )
      sql"""
        UPDATE tracking_exception_event
        SET resolved_at = ${java.sql.Timestamp.from(resolvedAt)},
            resolution_notes = $resolutionNotes,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = ${exceptionId.value}
      """.update.apply()

      val resolvedException = target.copy(
        resolvedAt = Some(resolvedAt),
        resolutionNotes = Some(resolutionNotes)
      )
      TrackingActivity.reconstruct(
        trackingNumber = activity.trackingNumber,
        bookingId = activity.bookingId,
        transportStatus = activity.transportStatus,
        events = activity.events,
        version = activity.version + 1,
        exceptions = activity.exceptions.updated(index, resolvedException)
      )
    }
