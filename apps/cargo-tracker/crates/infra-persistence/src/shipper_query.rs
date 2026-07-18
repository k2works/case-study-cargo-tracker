//! 荷主クエリポート（`ShipperQueryPort`）の sqlx 実装（CQRS 読み取り側・ADR-0001）。

use app_shipper::{QueryError, ShipperQueryPort, ShipperSummary};
use async_trait::async_trait;
use sqlx::PgPool;
use sqlx::Row;

/// PostgreSQL による荷主検索アダプター。
pub struct SqlxShipperQueryAdapter {
    pool: PgPool,
}

impl SqlxShipperQueryAdapter {
    /// コネクションプールからアダプターを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl ShipperQueryPort for SqlxShipperQueryAdapter {
    async fn search(&self, keyword: &str, limit: i64) -> Result<Vec<ShipperSummary>, QueryError> {
        let pattern = format!("%{keyword}%");
        let rows = sqlx::query(
            r"SELECT id, shipper_code, name, email
              FROM shipper
              WHERE name ILIKE $1 OR email ILIKE $1
              ORDER BY name
              LIMIT $2",
        )
        .bind(&pattern)
        .bind(limit)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| QueryError::Backend(e.to_string()))?;

        let mut summaries = Vec::with_capacity(rows.len());
        for row in rows {
            let id: uuid::Uuid = row
                .try_get("id")
                .map_err(|e| QueryError::Backend(e.to_string()))?;
            summaries.push(ShipperSummary {
                id: id.to_string(),
                code: row
                    .try_get("shipper_code")
                    .map_err(|e| QueryError::Backend(e.to_string()))?,
                name: row
                    .try_get("name")
                    .map_err(|e| QueryError::Backend(e.to_string()))?,
                email: row
                    .try_get("email")
                    .map_err(|e| QueryError::Backend(e.to_string()))?,
            });
        }
        Ok(summaries)
    }
}
