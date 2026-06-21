package cargotracker.routing.infrastructure.repositories

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.Location
import scalikejdbc.*

import javax.inject.Singleton

/** ScalikeJDBC 実装の VoyageRepository（US24/US25）。
  *
  *   - voyage と carrier_movement を 1 トランザクションで save する
  *   - 既存 VoyageNumber は UPDATE（version をインクリメント）+ carrier_movement を全削除して再挿入
  *   - 新規は INSERT
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

  override def findByVoyageNumber(voyageNumber: VoyageNumber): Option[Voyage] =
    DB.readOnly { implicit session =>
      sql"SELECT id, voyage_number, version FROM voyage WHERE voyage_number = ${voyageNumber.value}"
        .map { rs =>
          val rowId = rs.long("id")
          val vn = VoyageNumber.unsafeFrom(rs.string("voyage_number"))
          val ver = rs.int("version")
          (rowId, vn, ver)
        }
        .single
        .apply()
        .map { case (rowId, vn, ver) =>
          Voyage.reconstruct(vn, loadSchedule(rowId), ver)
        }
    }

  override def findAll(): Seq[Voyage] =
    DB.readOnly { implicit session =>
      val rows = sql"SELECT id, voyage_number, version FROM voyage ORDER BY voyage_number"
        .map { rs => (rs.long("id"), rs.string("voyage_number"), rs.int("version")) }
        .list
        .apply()
      rows.map { case (rowId, vn, ver) =>
        Voyage.reconstruct(VoyageNumber.unsafeFrom(vn), loadSchedule(rowId), ver)
      }
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
          // 楽観ロック: 期待 version と一致する行のみ更新。0 行ヒットは並行更新による競合
          val updated =
            sql"""UPDATE voyage SET version = version + 1, updated_at = CURRENT_TIMESTAMP
                  WHERE id = $id AND version = ${voyage.version}""".update.apply()
          if updated == 0 then
            throw cargotracker.shared.domain.OptimisticLockException(
              entityType = "Voyage",
              identifier = voyage.voyageNumber.value
            )
          // 更新時は carrier_movement を入れ替え
          sql"DELETE FROM carrier_movement WHERE voyage_id = $id".update.apply()
          id
        case None =>
          sql"""INSERT INTO voyage (voyage_number) VALUES (${voyage.voyageNumber.value})""".updateAndReturnGeneratedKey
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
    }
