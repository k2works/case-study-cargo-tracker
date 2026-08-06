package cargotracker.estimation.infrastructure.repositories

import cargotracker.estimation.domain.model.aggregates.{Estimate, EstimateStatus}
import cargotracker.estimation.domain.model.repositories.EstimateRepository
import cargotracker.estimation.domain.model.valueobjects.{CargoSpec, EstimateId, RouteCandidate, RouteSpec}
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
               deadline, cargo_type, weight_kg, status, version
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
                  estimateId = EstimateId.unsafeFrom(rs.string("estimate_id")),
                  routeSpec = RouteSpec(
                    origin = Location.unsafeFrom(rs.string("origin_unlocode")),
                    destination = Location.unsafeFrom(rs.string("destination_unlocode")),
                    deadline = rs.localDate("deadline")
                  ),
                  cargoSpec = CargoSpec(
                    cargoType = ct,
                    weight = Weight.unsafeFrom(rs.long("weight_kg"))
                  ),
                  status = st,
                  routeCandidates = candidates,
                  version = rs.int("version")
                )
              }
            }
        }
        .single
        .apply()
        .flatten
    }

  /** estimate と route_candidate を 2 クエリで取得し N+1 を解消する（IT4 タスク 0.7）。 */
  override def findAll(): Seq[Estimate] =
    DB.readOnly { implicit session =>
      case class EstimateRow(
          rowId: Long,
          estimateId: String,
          origin: String,
          destination: String,
          deadline: java.time.LocalDate,
          cargoType: String,
          weightKg: Long,
          status: String,
          version: Int
      )

      val rows: List[EstimateRow] =
        sql"""
          SELECT id, estimate_id, origin_unlocode, destination_unlocode,
                 deadline, cargo_type, weight_kg, status, version
          FROM estimate
          ORDER BY created_at DESC
        """
          .map { rs =>
            EstimateRow(
              rowId = rs.long("id"),
              estimateId = rs.string("estimate_id"),
              origin = rs.string("origin_unlocode"),
              destination = rs.string("destination_unlocode"),
              deadline = rs.localDate("deadline"),
              cargoType = rs.string("cargo_type"),
              weightKg = rs.long("weight_kg"),
              status = rs.string("status"),
              version = rs.int("version")
            )
          }
          .list
          .apply()

      if rows.isEmpty then Seq.empty
      else
        val rowIds = rows.map(_.rowId)
        val candidatesByEstimate: Map[Long, List[RouteCandidate]] =
          sql"""
            SELECT estimate_id, voyage_number, transit_ports, transit_days,
                   estimated_cost_amount, estimated_cost_currency
            FROM route_candidate
            WHERE estimate_id IN ($rowIds)
            ORDER BY estimate_id, id
          """
            .map { rs =>
              val cost = Money(
                rs.string("estimated_cost_currency"),
                rs.long("estimated_cost_amount")
              ).getOrElse(Money.zeroJpy)
              rs.long("estimate_id") -> RouteCandidate(
                voyageNumber = rs.string("voyage_number"),
                transitPorts = rs.string("transit_ports").split(",").toList,
                transitDays = rs.int("transit_days"),
                estimatedCost = cost
              )
            }
            .list
            .apply()
            .groupMap(_._1)(_._2)

        rows.flatMap { row =>
          for
            ct <- CargoType.fromName(row.cargoType)
            st <- EstimateStatus.fromName(row.status)
          yield Estimate.reconstruct(
            estimateId = EstimateId.unsafeFrom(row.estimateId),
            routeSpec = RouteSpec(
              origin = Location.unsafeFrom(row.origin),
              destination = Location.unsafeFrom(row.destination),
              deadline = row.deadline
            ),
            cargoSpec = CargoSpec(
              cargoType = ct,
              weight = Weight.unsafeFrom(row.weightKg)
            ),
            status = st,
            routeCandidates = candidatesByEstimate.getOrElse(row.rowId, Nil),
            version = row.version
          )
        }
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
