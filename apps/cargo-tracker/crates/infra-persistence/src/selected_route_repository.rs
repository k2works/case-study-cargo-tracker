//! `SelectedRouteRepository` の sqlx 実装（US09）。

use async_trait::async_trait;
use domain_routing::{RepositoryError, RouteCandidate, SelectedRouteRepository};
use sqlx::{PgPool, Row};

/// PostgreSQL による確定経路リポジトリ実装。
pub struct SqlxSelectedRouteRepository {
    pool: PgPool,
}

impl SqlxSelectedRouteRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn backend<E: std::fmt::Display>(e: E) -> RepositoryError {
    RepositoryError::Backend(e.to_string())
}

#[async_trait]
impl SelectedRouteRepository for SqlxSelectedRouteRepository {
    async fn save(&self, booking_id: &str, route: &RouteCandidate) -> Result<(), RepositoryError> {
        let mut tx = self.pool.begin().await.map_err(backend)?;

        // 予約番号を業務キーとして upsert し、子（区間）は洗い替えする。
        let route_id: i64 = sqlx::query(
            r"INSERT INTO selected_route (booking_id, status)
              VALUES ($1, 'SELECTED')
              ON CONFLICT (booking_id)
              DO UPDATE SET status = 'SELECTED', updated_at = NOW()
              RETURNING id",
        )
        .bind(booking_id)
        .fetch_one(&mut *tx)
        .await
        .map_err(backend)?
        .try_get("id")
        .map_err(backend)?;

        sqlx::query(r"DELETE FROM selected_route_leg WHERE selected_route_id = $1")
            .bind(route_id)
            .execute(&mut *tx)
            .await
            .map_err(backend)?;

        for (seq, leg) in route.segments().iter().enumerate() {
            sqlx::query(
                r"INSERT INTO selected_route_leg
                    (selected_route_id, voyage_number, load_location_unlocode,
                     unload_location_unlocode, load_time, unload_time, seq_number)
                  VALUES ($1, $2, $3, $4, $5, $6, $7)",
            )
            .bind(route_id)
            .bind(leg.voyage().as_str())
            .bind(leg.load_location().code())
            .bind(leg.unload_location().code())
            .bind(leg.load_time())
            .bind(leg.unload_time())
            .bind(i32::try_from(seq + 1).unwrap_or(i32::MAX))
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        }

        tx.commit().await.map_err(backend)?;
        Ok(())
    }

    async fn exists(&self, booking_id: &str) -> Result<bool, RepositoryError> {
        let row = sqlx::query(
            r"SELECT EXISTS(SELECT 1 FROM selected_route WHERE booking_id = $1) AS present",
        )
        .bind(booking_id)
        .fetch_one(&self.pool)
        .await
        .map_err(backend)?;
        row.try_get("present").map_err(backend)
    }
}
