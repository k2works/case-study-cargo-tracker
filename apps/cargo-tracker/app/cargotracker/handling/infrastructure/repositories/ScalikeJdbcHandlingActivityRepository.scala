package cargotracker.handling.infrastructure.repositories

import cargotracker.handling.domain.model.aggregates.HandlingActivity
import cargotracker.handling.domain.model.enums.HandlingType
import cargotracker.handling.domain.model.repositories.HandlingActivityRepository
import cargotracker.handling.domain.model.valueobjects.HandlingVoyageNumber
import cargotracker.shared.domain.Location
import scalikejdbc.*

import javax.inject.Singleton

@Singleton
class ScalikeJdbcHandlingActivityRepository extends HandlingActivityRepository:

  private def rowToActivity(rs: WrappedResultSet): Option[HandlingActivity] =
    for
      et <- HandlingType.fromName(rs.string("event_type"))
      loc <- Location(rs.string("location_unlocode")).toOption
    yield HandlingActivity.reconstruct(
      bookingId = rs.string("booking_id"),
      eventType = et,
      completionTime = rs.timestamp("event_completion_time").toInstant,
      location = loc,
      voyageNumber = rs.stringOpt("voyage_number").flatMap(HandlingVoyageNumber(_).toOption),
      operatorName = rs.stringOpt("operator_name"),
      routeDeviation = rs.boolean("route_deviation"),
      version = rs.int("version")
    )

  override def save(activity: HandlingActivity): Unit =
    DB.localTx { implicit session =>
      sql"""
        INSERT INTO handling_activity
          (booking_id, event_type, event_completion_time, location_unlocode,
           voyage_number, operator_name, route_deviation)
        VALUES
          (${activity.bookingId},
           ${activity.eventType.toString},
           ${java.sql.Timestamp.from(activity.completionTime)},
           ${activity.location.unLocode},
           ${activity.voyageNumber.map(_.value).orNull},
           ${activity.operatorName.orNull},
           ${activity.routeDeviation})
      """.update.apply()
    }

  override def findByBookingId(bookingId: String): Seq[HandlingActivity] =
    DB.readOnly { implicit session =>
      sql"""SELECT * FROM handling_activity WHERE booking_id = $bookingId
            ORDER BY event_completion_time"""
        .map(rowToActivity)
        .list
        .apply()
        .flatten
    }

  override def findAll(): Seq[HandlingActivity] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM handling_activity ORDER BY event_completion_time DESC"
        .map(rowToActivity)
        .list
        .apply()
        .flatten
    }
