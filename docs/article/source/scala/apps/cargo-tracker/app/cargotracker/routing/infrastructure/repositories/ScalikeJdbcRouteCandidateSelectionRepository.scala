package cargotracker.routing.infrastructure.repositories

import cargotracker.routing.domain.model.aggregates.RouteCandidateSelection
import cargotracker.routing.domain.model.repositories.RouteCandidateSelectionRepository
import cargotracker.routing.domain.model.valueobjects.{RouteSelectionStatus, VoyageNumber}
import cargotracker.shared.domain.OptimisticLockException
import scalikejdbc.*

import javax.inject.Singleton

/** ScalikeJDBC 実装の経路選択リポジトリ（US09 / V9 マイグレーション）。
  *
  *   - 1 予約 1 選択（`booking_id` UNIQUE）
  *   - `voyage_numbers` はカンマ区切りで順序保持
  *   - 楽観ロックを `version` カラムで実装
  */
@Singleton
class ScalikeJdbcRouteCandidateSelectionRepository extends RouteCandidateSelectionRepository:

  override def findByBookingId(bookingId: String): Option[RouteCandidateSelection] =
    DB.readOnly { implicit session =>
      sql"""SELECT booking_id, voyage_numbers, status, version
            FROM route_candidate_selection
            WHERE booking_id = $bookingId"""
        .map { rs =>
          val voyages = rs.string("voyage_numbers").split(",").toList.map(VoyageNumber.unsafeFrom)
          val status = RouteSelectionStatus.fromName(rs.string("status")).getOrElse(RouteSelectionStatus.Pending)
          RouteCandidateSelection.reconstruct(
            bookingId = rs.string("booking_id"),
            voyages = voyages,
            status = status,
            version = rs.int("version")
          )
        }
        .single
        .apply()
    }

  override def save(selection: RouteCandidateSelection): Unit =
    DB.localTx { implicit session =>
      val voyageNumbers = selection.voyages.map(_.value).mkString(",")
      val statusName = selection.status.toString
      val existingVersion =
        sql"""SELECT version FROM route_candidate_selection
              WHERE booking_id = ${selection.bookingId}"""
          .map(_.int("version"))
          .single
          .apply()

      existingVersion match
        case None =>
          sql"""INSERT INTO route_candidate_selection
                  (booking_id, voyage_numbers, status, version)
                VALUES
                  (${selection.bookingId}, $voyageNumbers, $statusName, ${selection.version + 1})""".update
            .apply()
        case Some(persisted) =>
          if persisted != selection.version then
            throw OptimisticLockException("route_candidate_selection", selection.bookingId)
          val updated =
            sql"""UPDATE route_candidate_selection
                  SET voyage_numbers = $voyageNumbers,
                      status = $statusName,
                      version = version + 1,
                      updated_at = CURRENT_TIMESTAMP
                  WHERE booking_id = ${selection.bookingId}
                    AND version = ${selection.version}""".update.apply()
          if updated == 0 then throw OptimisticLockException("route_candidate_selection", selection.bookingId)
    }
