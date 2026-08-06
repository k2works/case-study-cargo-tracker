//! `domain_routing::CargoSpecProvider` の sqlx 実装。
//!
//! Routing Context の経路探索が必要とする貨物仕様を、Booking Context の `cargo` テーブルから
//! 予約番号（booking_id）で射影して返す ACL（腐敗防止層）。domain-booking のドメイン型には依存せず、
//! テーブルの生値を Routing の型（`Location`・`CargoType`）へ変換する。

use async_trait::async_trait;
use chrono::NaiveDate;
use domain_routing::{AclError, CargoSpec, CargoSpecProvider, CargoType};
use shared_kernel::Location;
use sqlx::{PgPool, Row};

/// PostgreSQL による貨物仕様プロバイダ実装。
pub struct SqlxCargoSpecProvider {
    pool: PgPool,
}

impl SqlxCargoSpecProvider {
    /// コネクションプールからプロバイダを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn acl<E: std::fmt::Display>(e: E) -> AclError {
    AclError::Backend(e.to_string())
}

#[async_trait]
impl CargoSpecProvider for SqlxCargoSpecProvider {
    async fn find_cargo_spec(&self, booking_id: &str) -> Result<Option<CargoSpec>, AclError> {
        let row = sqlx::query(
            r"SELECT origin_unlocode, destination_unlocode, arrival_deadline, cargo_type
              FROM cargo WHERE booking_id = $1",
        )
        .bind(booking_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(acl)?;

        let Some(row) = row else {
            return Ok(None);
        };

        let origin: String = row.try_get("origin_unlocode").map_err(acl)?;
        let destination: String = row.try_get("destination_unlocode").map_err(acl)?;
        let arrival_deadline: NaiveDate = row.try_get("arrival_deadline").map_err(acl)?;
        let cargo_type: String = row.try_get("cargo_type").map_err(acl)?;

        Ok(Some(CargoSpec {
            origin: Location::new(&origin).map_err(acl)?,
            destination: Location::new(&destination).map_err(acl)?,
            arrival_deadline,
            cargo_type: CargoType::parse(&cargo_type).map_err(acl)?,
        }))
    }
}
