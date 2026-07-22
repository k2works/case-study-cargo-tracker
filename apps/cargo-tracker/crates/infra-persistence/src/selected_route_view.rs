//! `SelectedRouteView` の sqlx 実装（US11/US12）。
//!
//! Booking Context への逆方向 ACL。確定経路（`selected_route`/`selected_route_leg`）を
//! 読み取り、通知内容組み立て用の要約（経由港・所要日数・到着予定日）を返す。

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use domain_booking::{AclError, SelectedRouteSummary, SelectedRouteView};
use sqlx::{PgPool, Row};

/// PostgreSQL による確定経路の読み取りビュー実装。
pub struct SqlxSelectedRouteView {
    pool: PgPool,
}

impl SqlxSelectedRouteView {
    /// コネクションプールからビューを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn acl<E: std::fmt::Display>(e: E) -> AclError {
    AclError::Backend(e.to_string())
}

#[async_trait]
impl SelectedRouteView for SqlxSelectedRouteView {
    async fn find_by_booking(
        &self,
        booking_id: &str,
    ) -> Result<Option<SelectedRouteSummary>, AclError> {
        // 区間を順序どおりに取得する。存在しなければ未確定。
        let rows = sqlx::query(
            r"SELECT srl.voyage_number, srl.load_location_unlocode, srl.unload_location_unlocode,
                     srl.load_time, srl.unload_time
              FROM selected_route sr
              JOIN selected_route_leg srl ON srl.selected_route_id = sr.id
              WHERE sr.booking_id = $1
              ORDER BY srl.seq_number",
        )
        .bind(booking_id)
        .fetch_all(&self.pool)
        .await
        .map_err(acl)?;

        if rows.is_empty() {
            return Ok(None);
        }

        let mut voyage_numbers = Vec::with_capacity(rows.len());
        let mut transit_ports = Vec::with_capacity(rows.len() + 1);
        for (i, row) in rows.iter().enumerate() {
            voyage_numbers.push(row.try_get::<String, _>("voyage_number").map_err(acl)?);
            if i == 0 {
                transit_ports.push(
                    row.try_get::<String, _>("load_location_unlocode")
                        .map_err(acl)?,
                );
            }
            transit_ports.push(
                row.try_get::<String, _>("unload_location_unlocode")
                    .map_err(acl)?,
            );
        }

        let first_load: DateTime<Utc> = rows[0].try_get("load_time").map_err(acl)?;
        let last_unload: DateTime<Utc> =
            rows[rows.len() - 1].try_get("unload_time").map_err(acl)?;
        let transit_days = (last_unload - first_load).num_days();
        let expected_arrival = last_unload.date_naive().format("%Y-%m-%d").to_string();

        Ok(Some(SelectedRouteSummary {
            voyage_numbers,
            transit_ports,
            transit_days,
            expected_arrival,
        }))
    }
}
