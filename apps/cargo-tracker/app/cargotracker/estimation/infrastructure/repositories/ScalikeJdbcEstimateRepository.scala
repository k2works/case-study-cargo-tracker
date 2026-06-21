package cargotracker.estimation.infrastructure.repositories

import cargotracker.estimation.domain.model.aggregates.{Estimate, EstimateStatus}
import cargotracker.estimation.domain.model.repositories.EstimateRepository
import cargotracker.estimation.domain.model.valueobjects.{EstimateId, RouteCandidate}
import cargotracker.shared.domain.{CargoType, Location, Money, Weight}
import scalikejdbc.*

import javax.inject.Singleton

@Singleton
class ScalikeJdbcEstimateRepository extends EstimateRepository:

  private def loadCandidates(
      estimateRowId: Long
  )(implicit session: DBSession): List[RouteCandidate] =
    sql"""
      SELECT voyage_number, transit_ports, transit_days,
             estimated_cost_amount, estimated_cost_currency
      FROM route_candidate
      WHERE estimate_id = $estimateRowId
      ORDER BY id
    """
      .map { rs =>
        val cost = Money(
          rs.string("estimated_cost_currency"),
          rs.long("estimated_cost_amount")
        ).getOrElse(Money.zeroJpy)
        RouteCandidate(
          voyageNumber = rs.string("voyage_number"),
          transitPorts = rs.string("transit_ports").split(",").toList,
          transitDays = rs.int("transit_days"),
          estimatedCost = cost
        )
      }
      .list
      .apply()

  override def findById(estimateId: EstimateId): Option[Estimate] =
    DB.readOnly { implicit session =>
      sql"""
        SELECT id, estimate_id, origin_unlocode, destination_unlocode,
               deadline, cargo_type, weight_kg, status
        FROM estimate
        WHERE estimate_id = ${estimateId.value}
      """
        .map { rs =>
          val rowId = rs.long("id")
          val candidates = loadCandidates(rowId)
          CargoType
            .fromName(rs.string("cargo_type"))
            .flatMap { ct =>
              EstimateStatus.fromName(rs.string("status")).map { st =>
                Estimate.reconstruct(
                  EstimateId.unsafeFrom(rs.string("estimate_id")),
                  Location.unsafeFrom(rs.string("origin_unlocode")),
                  Location.unsafeFrom(rs.string("destination_unlocode")),
                  rs.localDate("deadline"),
                  ct,
                  Weight.unsafeFrom(rs.long("weight_kg")),
                  st,
                  candidates
                )
              }
            }
        }
        .single
        .apply()
        .flatten
    }

  override def findAll(): Seq[Estimate] =
    DB.readOnly { implicit session =>
      val ids = sql"SELECT estimate_id FROM estimate ORDER BY created_at DESC"
        .map(_.string("estimate_id"))
        .list
        .apply()
      ids.flatMap(id => findById(EstimateId.unsafeFrom(id)))
    }

  override def save(estimate: Estimate): Unit =
    DB.localTx { implicit session =>
      val estimateRowId =
        sql"""
          INSERT INTO estimate
            (estimate_id, origin_unlocode, destination_unlocode,
             deadline, cargo_type, weight_kg, status)
          VALUES
            (${estimate.estimateId.value},
             ${estimate.origin.unLocode},
             ${estimate.destination.unLocode},
             ${estimate.deadline},
             ${estimate.cargoType.toString},
             ${estimate.weight.kg},
             ${estimate.status.toString})
        """.updateAndReturnGeneratedKey.apply()

      estimate.routeCandidates.foreach { rc =>
        sql"""
          INSERT INTO route_candidate
            (estimate_id, voyage_number, transit_ports, transit_days,
             estimated_cost_amount, estimated_cost_currency)
          VALUES
            ($estimateRowId, ${rc.voyageNumber},
             ${rc.transitPorts.mkString(",")},
             ${rc.transitDays},
             ${rc.estimatedCost.amount},
             ${rc.estimatedCost.currency})
        """.update.apply()
      }
    }
