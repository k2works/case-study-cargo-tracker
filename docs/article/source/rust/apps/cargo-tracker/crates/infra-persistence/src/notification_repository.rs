//! `NotificationPort` の sqlx 実装（US06/US12/US13）。
//!
//! 本 IT では「送信＝記録」に限定し、`notification` テーブルへ通知内容を保存する。
//! 実際のメール / SMS 配信はスコープ外。

use async_trait::async_trait;
use domain_booking::{Notification, NotificationPort, RepositoryError};
use sqlx::{PgPool, Row};

/// PostgreSQL による通知記録実装。
pub struct SqlxNotificationRepository {
    pool: PgPool,
}

impl SqlxNotificationRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// 指定予約に紐づく通知件数を返す（テスト・検証用）。
    ///
    /// # Errors
    ///
    /// クエリ実行に失敗した場合は `RepositoryError::Backend` を返す。
    pub async fn count_by_booking_id(&self, booking_id: &str) -> Result<i64, RepositoryError> {
        let count: i64 =
            sqlx::query(r"SELECT COUNT(*) AS c FROM notification WHERE booking_id = $1")
                .bind(booking_id)
                .fetch_one(&self.pool)
                .await
                .map_err(backend)?
                .try_get("c")
                .map_err(backend)?;
        Ok(count)
    }
}

fn backend<E: std::fmt::Display>(e: E) -> RepositoryError {
    RepositoryError::Backend(e.to_string())
}

#[async_trait]
impl NotificationPort for SqlxNotificationRepository {
    async fn notify(&self, notification: &Notification) -> Result<(), RepositoryError> {
        sqlx::query(
            r"INSERT INTO notification
                (booking_id, notification_type, recipient_role, recipient_email, subject, body)
              VALUES ($1, $2, $3, $4, $5, $6)",
        )
        .bind(notification.booking_id().as_str())
        .bind(notification.notification_type().as_str())
        .bind(notification.recipient_role().as_str())
        .bind(notification.recipient_email())
        .bind(notification.subject())
        .bind(notification.body())
        .execute(&self.pool)
        .await
        .map_err(backend)?;
        Ok(())
    }
}
