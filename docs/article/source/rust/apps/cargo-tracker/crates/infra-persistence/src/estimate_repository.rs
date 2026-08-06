//! `EstimateRepository` の sqlx 実装（US01）。
//!
//! `estimate`（集約ルート）と `route_candidate`（ルート候補）をトランザクションで保存し、
//! 見積 ID・一覧で再構築する。

use async_trait::async_trait;
use domain_estimation::{
    CargoType, Estimate, EstimateId, EstimateLocation, EstimateRepository, EstimateStatus,
    EstimationRepositoryError, RouteCandidate, Weight,
};
use sqlx::{PgPool, Row};

/// PostgreSQL による見積リポジトリ実装。
pub struct SqlxEstimateRepository {
    pool: PgPool,
}

impl SqlxEstimateRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn backend<E: std::fmt::Display>(e: E) -> EstimationRepositoryError {
    EstimationRepositoryError::Storage(e.to_string())
}

fn reconstruct_candidate(
    row: &sqlx::postgres::PgRow,
) -> Result<RouteCandidate, EstimationRepositoryError> {
    let voyage_number: String = row.try_get("voyage_number").map_err(backend)?;
    let transit_port: Option<String> = row.try_get("transit_port").map_err(backend)?;
    let transit_days: i32 = row.try_get("transit_days").map_err(backend)?;
    let estimated_cost: rust_decimal::Decimal = row.try_get("estimated_cost").map_err(backend)?;
    let rank: i32 = row.try_get("rank").map_err(backend)?;
    let ports = transit_port.into_iter().collect();
    Ok(RouteCandidate::new(
        voyage_number,
        ports,
        transit_days,
        estimated_cost,
        rank,
    ))
}

impl SqlxEstimateRepository {
    async fn reconstruct_estimate(
        &self,
        row: &sqlx::postgres::PgRow,
    ) -> Result<Estimate, EstimationRepositoryError> {
        let id: i64 = row.try_get("id").map_err(backend)?;
        let estimate_uuid: uuid::Uuid = row.try_get("estimate_id").map_err(backend)?;
        let origin: String = row.try_get("origin_unlocode").map_err(backend)?;
        let destination: String = row.try_get("destination_unlocode").map_err(backend)?;
        let deadline: chrono::NaiveDate = row.try_get("arrival_deadline").map_err(backend)?;
        let cargo_type: String = row.try_get("cargo_type").map_err(backend)?;
        let weight_kg: rust_decimal::Decimal = row.try_get("weight_kg").map_err(backend)?;
        let status: String = row.try_get("status").map_err(backend)?;

        let candidate_rows = sqlx::query(
            r"SELECT voyage_number, transit_port, transit_days, estimated_cost, rank
              FROM route_candidate WHERE estimate_id = $1 ORDER BY rank ASC",
        )
        .bind(id)
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;
        let candidates = candidate_rows
            .iter()
            .map(reconstruct_candidate)
            .collect::<Result<Vec<_>, _>>()?;

        Ok(Estimate::reconstruct(
            EstimateId::from_string(format!("EST-{}", estimate_uuid.simple())),
            EstimateLocation::new(&origin).map_err(backend)?,
            EstimateLocation::new(&destination).map_err(backend)?,
            deadline,
            CargoType::from_str_or_general(&cargo_type),
            Weight::new(weight_kg).map_err(backend)?,
            EstimateStatus::from_str_or_created(&status),
            candidates,
        ))
    }
}

/// `EstimateId` の文字列（EST-... または UUID 文字列）から UUID を抽出する。
fn estimate_uuid(id: &EstimateId) -> Result<uuid::Uuid, EstimationRepositoryError> {
    let raw = id.as_str().strip_prefix("EST-").unwrap_or(id.as_str());
    uuid::Uuid::parse_str(raw).map_err(backend)
}

#[async_trait]
impl EstimateRepository for SqlxEstimateRepository {
    async fn save(&self, estimate: &Estimate) -> Result<(), EstimationRepositoryError> {
        let mut tx = self.pool.begin().await.map_err(backend)?;
        let uuid = estimate_uuid(estimate.estimate_id())?;

        let estimate_pk: i64 = sqlx::query(
            r"INSERT INTO estimate
                (estimate_id, origin_unlocode, destination_unlocode, arrival_deadline,
                 cargo_type, weight_kg, status)
              VALUES ($1, $2, $3, $4, $5, $6, $7)
              ON CONFLICT (estimate_id)
              DO UPDATE SET status = EXCLUDED.status, updated_at = NOW()
              RETURNING id",
        )
        .bind(uuid)
        .bind(estimate.origin().un_locode())
        .bind(estimate.destination().un_locode())
        .bind(estimate.arrival_deadline())
        .bind(estimate.cargo_type().as_str())
        .bind(estimate.weight().value())
        .bind(estimate.status().as_str())
        .fetch_one(&mut *tx)
        .await
        .map_err(backend)?
        .try_get("id")
        .map_err(backend)?;

        // ルート候補は洗い替え。
        sqlx::query(r"DELETE FROM route_candidate WHERE estimate_id = $1")
            .bind(estimate_pk)
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        for c in estimate.candidates() {
            sqlx::query(
                r"INSERT INTO route_candidate
                    (estimate_id, voyage_number, transit_port, transit_days, estimated_cost, rank)
                  VALUES ($1, $2, $3, $4, $5, $6)",
            )
            .bind(estimate_pk)
            .bind(c.voyage_number())
            .bind(c.transit_ports().first().map(String::as_str))
            .bind(c.transit_days())
            .bind(c.estimated_cost())
            .bind(c.rank())
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        }

        tx.commit().await.map_err(backend)?;
        Ok(())
    }

    async fn find_by_id(
        &self,
        id: &EstimateId,
    ) -> Result<Option<Estimate>, EstimationRepositoryError> {
        let uuid = estimate_uuid(id)?;
        let row = sqlx::query(
            r"SELECT id, estimate_id, origin_unlocode, destination_unlocode, arrival_deadline,
                     cargo_type, weight_kg, status
              FROM estimate WHERE estimate_id = $1",
        )
        .bind(uuid)
        .fetch_optional(&self.pool)
        .await
        .map_err(backend)?;
        match row {
            Some(r) => Ok(Some(self.reconstruct_estimate(&r).await?)),
            None => Ok(None),
        }
    }

    async fn find_all(&self) -> Result<Vec<Estimate>, EstimationRepositoryError> {
        let rows = sqlx::query(
            r"SELECT id, estimate_id, origin_unlocode, destination_unlocode, arrival_deadline,
                     cargo_type, weight_kg, status
              FROM estimate ORDER BY created_at DESC, id DESC",
        )
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;
        let mut estimates = Vec::with_capacity(rows.len());
        for r in &rows {
            estimates.push(self.reconstruct_estimate(r).await?);
        }
        Ok(estimates)
    }
}
