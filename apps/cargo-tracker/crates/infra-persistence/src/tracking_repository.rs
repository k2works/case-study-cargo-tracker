//! `TrackingActivityRepository` の sqlx 実装（US14/US15/US17）。
//!
//! `tracking_activity`（集約ルート）と `tracking_handling_event`（イベント履歴）を
//! トランザクションで保存し、追跡番号で再構築する。

use async_trait::async_trait;
use domain_tracking::{
    TrackingActivity, TrackingActivityEvent, TrackingActivityRepository, TrackingBookingId,
    TrackingLocation, TrackingNumber, TrackingRepositoryError, TrackingStatus,
    TrackingVoyageNumber,
};
use sqlx::{PgPool, Row};

/// PostgreSQL による追跡活動リポジトリ実装。
pub struct SqlxTrackingActivityRepository {
    pool: PgPool,
}

impl SqlxTrackingActivityRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn backend<E: std::fmt::Display>(e: E) -> TrackingRepositoryError {
    TrackingRepositoryError::Storage(e.to_string())
}

#[async_trait]
impl TrackingActivityRepository for SqlxTrackingActivityRepository {
    async fn save(&self, activity: &TrackingActivity) -> Result<(), TrackingRepositoryError> {
        let mut tx = self.pool.begin().await.map_err(backend)?;

        // 追跡番号を業務キーとして upsert する。
        // NOTE: transport_status は `current_status()`（イベント列からの純粋関数導出）の
        // 書き込み時キャッシュ（Read Model・ADR-0006）。正典は tracking_handling_event 列であり、
        // 書き込み経路が本 save のみに統制される限り整合する。追跡照会（US18）はこの列を用い、
        // 再導出コストを避ける。二重管理ではなく Read Model への意図的な射影である。
        let tracking_id: i64 = sqlx::query(
            r"INSERT INTO tracking_activity (tracking_number, booking_id, transport_status)
              VALUES ($1, $2, $3)
              ON CONFLICT (tracking_number)
              DO UPDATE SET transport_status = EXCLUDED.transport_status, updated_at = NOW()
              RETURNING id",
        )
        .bind(activity.tracking_number().as_str())
        .bind(activity.booking_id().as_str())
        .bind(activity.current_status().as_str())
        .fetch_one(&mut *tx)
        .await
        .map_err(backend)?
        .try_get("id")
        .map_err(backend)?;

        // イベントは洗い替え（集約単位で一貫させる）。
        sqlx::query(r"DELETE FROM tracking_handling_event WHERE tracking_id = $1")
            .bind(tracking_id)
            .execute(&mut *tx)
            .await
            .map_err(backend)?;

        for event in activity.events() {
            sqlx::query(
                r"INSERT INTO tracking_handling_event
                    (tracking_id, event_type, event_time, location_unlocode, voyage_number)
                  VALUES ($1, $2, $3, $4, $5)",
            )
            .bind(tracking_id)
            .bind(event.status().as_str())
            .bind(event.event_time())
            .bind(event.location().un_locode())
            .bind(event.voyage_number().map(TrackingVoyageNumber::as_str))
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        }

        tx.commit().await.map_err(backend)?;
        Ok(())
    }

    async fn find_by_tracking_number(
        &self,
        number: &TrackingNumber,
    ) -> Result<Option<TrackingActivity>, TrackingRepositoryError> {
        let Some(header) =
            sqlx::query(r"SELECT id, booking_id FROM tracking_activity WHERE tracking_number = $1")
                .bind(number.as_str())
                .fetch_optional(&self.pool)
                .await
                .map_err(backend)?
        else {
            return Ok(None);
        };

        let tracking_id: i64 = header.try_get("id").map_err(backend)?;
        let booking_id: String = header.try_get("booking_id").map_err(backend)?;

        let rows = sqlx::query(
            r"SELECT event_type, event_time, location_unlocode, voyage_number
              FROM tracking_handling_event
              WHERE tracking_id = $1
              ORDER BY event_time ASC, id ASC",
        )
        .bind(tracking_id)
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;

        let mut events = Vec::with_capacity(rows.len());
        for row in rows {
            let status_str: String = row.try_get("event_type").map_err(backend)?;
            let un_locode: String = row.try_get("location_unlocode").map_err(backend)?;
            let voyage: Option<String> = row.try_get("voyage_number").map_err(backend)?;
            let location = TrackingLocation::new(&un_locode).map_err(backend)?;
            events.push(TrackingActivityEvent::new(
                TrackingStatus::from_str_or_unknown(&status_str),
                location,
                row.try_get("event_time").map_err(backend)?,
                voyage.and_then(TrackingVoyageNumber::new),
            ));
        }

        let booking_ref = TrackingBookingId::parse(booking_id).map_err(backend)?;
        Ok(Some(TrackingActivity::reconstruct(
            number.clone(),
            booking_ref,
            events,
        )))
    }

    async fn find_by_booking_id(
        &self,
        booking_id: &str,
    ) -> Result<Option<TrackingActivity>, TrackingRepositoryError> {
        // 予約 ID から追跡番号を引き、既存の再構築ロジックへ委譲する（ADR-0006 冪等性保証）。
        let row = sqlx::query(
            r"SELECT tracking_number FROM tracking_activity WHERE booking_id = $1
              ORDER BY id ASC LIMIT 1",
        )
        .bind(booking_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(backend)?;
        let Some(row) = row else {
            return Ok(None);
        };
        let number_str: String = row.try_get("tracking_number").map_err(backend)?;
        let number = TrackingNumber::parse(number_str).map_err(backend)?;
        self.find_by_tracking_number(&number).await
    }
}
