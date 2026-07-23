//! `HandlingActivityRepository` の sqlx 実装（US15/US16）。

use async_trait::async_trait;
use domain_handling::{
    HandlingActivity, HandlingActivityRepository, HandlingLocation, HandlingRepositoryError,
    HandlingType, ReceiptConfirmation,
};
use sqlx::{PgPool, Row};

/// PostgreSQL による荷役作業リポジトリ実装。
pub struct SqlxHandlingActivityRepository {
    pool: PgPool,
}

impl SqlxHandlingActivityRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn backend<E: std::fmt::Display>(e: E) -> HandlingRepositoryError {
    HandlingRepositoryError::Storage(e.to_string())
}

#[async_trait]
impl HandlingActivityRepository for SqlxHandlingActivityRepository {
    async fn save(&self, activity: &HandlingActivity) -> Result<(), HandlingRepositoryError> {
        sqlx::query(
            r"INSERT INTO handling_activity
                (booking_id, event_type, event_completion_time, location_unlocode,
                 voyage_number, operator_name, receipt_confirmation)
              VALUES ($1, $2, $3, $4, $5, $6, $7)",
        )
        .bind(activity.booking_id())
        .bind(activity.handling_type().as_str())
        .bind(activity.completion_time())
        .bind(activity.location().un_locode())
        .bind(activity.voyage_number())
        .bind(activity.operator_name())
        .bind(
            activity
                .receipt_confirmation()
                .map(ReceiptConfirmation::as_str),
        )
        .execute(&self.pool)
        .await
        .map_err(backend)?;
        Ok(())
    }

    async fn find_by_booking_id(
        &self,
        booking_id: &str,
    ) -> Result<Vec<HandlingActivity>, HandlingRepositoryError> {
        let rows = sqlx::query(
            r"SELECT event_type, event_completion_time, location_unlocode,
                     voyage_number, operator_name, receipt_confirmation
              FROM handling_activity
              WHERE booking_id = $1
              ORDER BY event_completion_time DESC, id DESC",
        )
        .bind(booking_id)
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;

        let mut activities = Vec::with_capacity(rows.len());
        for row in rows {
            let type_str: String = row.try_get("event_type").map_err(backend)?;
            let un_locode: String = row.try_get("location_unlocode").map_err(backend)?;
            let voyage: Option<String> = row.try_get("voyage_number").map_err(backend)?;
            let operator: Option<String> = row.try_get("operator_name").map_err(backend)?;
            let receipt: Option<String> = row.try_get("receipt_confirmation").map_err(backend)?;

            let handling_type = HandlingType::parse_type(&type_str)
                .ok_or_else(|| backend(format!("未知の荷役種別: {type_str}")))?;
            let location = HandlingLocation::new(&un_locode).map_err(backend)?;
            let receipt_confirmation = match receipt {
                Some(v) => Some(ReceiptConfirmation::new(v).map_err(backend)?),
                None => None,
            };
            let activity = HandlingActivity::register(
                booking_id.to_string(),
                handling_type,
                row.try_get("event_completion_time").map_err(backend)?,
                location,
                voyage,
                operator,
                receipt_confirmation,
            )
            .map_err(backend)?;
            activities.push(activity);
        }
        Ok(activities)
    }
}
