package cargotracker.tracking.infrastructure.repositories

import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.{TrackingBookingId, TrackingNumber}
import scalikejdbc.*

import javax.inject.Singleton

@Singleton
class ScalikeJdbcTrackingActivityRepository extends TrackingActivityRepository:

  private def rowToActivity(rs: WrappedResultSet): Option[TrackingActivity] =
    for
      tn <- TrackingNumber(rs.string("tracking_number")).toOption
      bid <- TrackingBookingId(rs.string("booking_id")).toOption
      status = TrackingStatus.values
        .find(_.toString == rs.string("transport_status"))
        .getOrElse(TrackingStatus.Unknown)
    yield TrackingActivity.reconstruct(tn, bid, status, rs.int("version"))

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
    }

  override def findByBookingId(bid: TrackingBookingId): Option[TrackingActivity] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM tracking_activity WHERE booking_id = ${bid.value}"
        .map(rowToActivity)
        .single
        .apply()
        .flatten
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
