package cargotracker.routing.infrastructure.repositories

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.{CargoType, Location}
import scalikejdbc.*

import javax.inject.Singleton

/** ScalikeJDBC 実装の VoyageRepository（US24/US25 + ADR 0006 拡張）。
  *
  *   - voyage + carrier_movement + voyage_supported_cargo_type を 1 トランザクションで save
  *   - 更新時は carrier_movement と voyage_supported_cargo_type を全削除して再挿入
  */
@Singleton
class ScalikeJdbcVoyageRepository extends VoyageRepository:

  private def rowToCarrierMovement(rs: WrappedResultSet): Option[CarrierMovement] =
    CarrierMovement(
      Location.unsafeFrom(rs.string("departure_location_unlocode")),
      Location.unsafeFrom(rs.string("arrival_location_unlocode")),
      rs.zonedDateTime("departure_date").toInstant,
      rs.zonedDateTime("arrival_date").toInstant
    ).toOption

  private def loadSchedule(voyageRowId: Long)(implicit session: DBSession): Schedule =
    val movements =
      sql"""SELECT * FROM carrier_movement
            WHERE voyage_id = $voyageRowId
            ORDER BY seq_number"""
        .map(rowToCarrierMovement)
        .list
        .apply()
        .flatten
    Schedule(movements)
      .getOrElse(
        throw new IllegalStateException(
          s"voyage_id=$voyageRowId の carrier_movement が Schedule 不変条件を満たさない"
        )
      )

  private def loadSupportedCargoTypes(voyageRowId: Long)(implicit
      session: DBSession
  ): Set[CargoType] =
    sql"SELECT cargo_type FROM voyage_supported_cargo_type WHERE voyage_id = $voyageRowId"
      .map(rs => CargoType.fromName(rs.string("cargo_type")))
      .list
      .apply()
      .flatten
      .toSet

  private case class VoyageRow(
      id: Long,
      voyageNumber: String,
      version: Int,
      vesselName: String,
      carrierCode: String
  )

  private def extractRow(rs: WrappedResultSet): VoyageRow =
    VoyageRow(
      id = rs.long("id"),
      voyageNumber = rs.string("voyage_number"),
      version = rs.int("version"),
      vesselName = rs.string("vessel_name"),
      carrierCode = rs.string("carrier_code")
    )

  private def reconstructVoyage(row: VoyageRow)(implicit session: DBSession): Voyage =
    Voyage.reconstruct(
      voyageNumber = VoyageNumber.unsafeFrom(row.voyageNumber),
      schedule = loadSchedule(row.id),
      vesselName = row.vesselName,
      carrierCode = row.carrierCode,
      supportedCargoTypes = loadSupportedCargoTypes(row.id),
      version = row.version
    )

  override def findByVoyageNumber(voyageNumber: VoyageNumber): Option[Voyage] =
    DB.readOnly { implicit session =>
      sql"""SELECT id, voyage_number, version, vessel_name, carrier_code
            FROM voyage WHERE voyage_number = ${voyageNumber.value}"""
        .map(extractRow)
        .single
        .apply()
        .map(reconstructVoyage)
    }

  override def findAll(): Seq[Voyage] =
    DB.readOnly { implicit session =>
      sql"""SELECT id, voyage_number, version, vessel_name, carrier_code
            FROM voyage ORDER BY voyage_number"""
        .map(extractRow)
        .list
        .apply()
        .map(reconstructVoyage)
    }

  override def save(voyage: Voyage): Unit =
    DB.localTx { implicit session =>
      val existingId =
        sql"SELECT id FROM voyage WHERE voyage_number = ${voyage.voyageNumber.value}"
          .map(_.long("id"))
          .single
          .apply()

      val voyageRowId = existingId match
        case Some(id) =>
          val updated =
            sql"""UPDATE voyage
                  SET version = version + 1,
                      vessel_name = ${voyage.vesselName},
                      carrier_code = ${voyage.carrierCode},
                      updated_at = CURRENT_TIMESTAMP
                  WHERE id = $id AND version = ${voyage.version}""".update.apply()
          if updated == 0 then
            throw cargotracker.shared.domain.OptimisticLockException(
              entityType = "Voyage",
              identifier = voyage.voyageNumber.value
            )
          sql"DELETE FROM carrier_movement WHERE voyage_id = $id".update.apply()
          sql"DELETE FROM voyage_supported_cargo_type WHERE voyage_id = $id".update.apply()
          id
        case None =>
          sql"""INSERT INTO voyage (voyage_number, vessel_name, carrier_code)
                VALUES (${voyage.voyageNumber.value}, ${voyage.vesselName}, ${voyage.carrierCode})""".updateAndReturnGeneratedKey
            .apply()

      voyage.schedule.carrierMovements.zipWithIndex.foreach { case (cm, idx) =>
        sql"""INSERT INTO carrier_movement
                (voyage_id, departure_location_unlocode, arrival_location_unlocode,
                 departure_date, arrival_date, seq_number)
              VALUES
                ($voyageRowId, ${cm.departureLocation.unLocode},
                 ${cm.arrivalLocation.unLocode},
                 ${java.sql.Timestamp.from(cm.departureTime)},
                 ${java.sql.Timestamp.from(cm.arrivalTime)},
                 ${idx + 1})""".update.apply()
      }

      voyage.supportedCargoTypes.foreach { ct =>
        sql"""INSERT INTO voyage_supported_cargo_type (voyage_id, cargo_type)
              VALUES ($voyageRowId, ${ct.toString})""".update.apply()
      }
    }
