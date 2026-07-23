//! Tracking / Handling の BC 跨ぎ連携を担う ACL アダプター（composition 層）。
//!
//! app-tracking / app-handling が定義するポートを、`domain-booking`・`domain-tracking`・
//! インフラ実装へ橋渡しする。ドメインクレート同士は依存せず、結線はここに閉じ込める
//! （BC 独立・ADR-0004）。

use app_handling::{HandlingServiceError, RouteCheck, RouteCheckPort, TrackingReflectionPort};
use app_tracking::{
    ConfirmedBookingInfo, ConfirmedBookingIssuer, TrackingNotificationPort, TrackingServiceError,
};
use async_trait::async_trait;
use chrono::{DateTime, Utc};
use domain_booking::{BookingId, CargoRepository, SelectedRouteView};
use domain_tracking::{
    TrackingActivityEvent, TrackingActivityRepository, TrackingLocation, TrackingNumber,
    TrackingStatus, TrackingVoyageNumber,
};
use sqlx::PgPool;
use std::sync::Arc;

/// 通知テーブルへ 1 件記録する（tracking 由来の通知。送信＝記録）。
/// 宛先が解決できなかった場合のフォールバック連絡先（運用担当へ通知が滞留しないための保険）。
const FALLBACK_RECIPIENT: &str = "ops@cargotracker.example";

/// `booking_id` から荷受人の連絡先（通知宛先）を解決する。
///
/// 予約（`cargo`）の `consignee_email` を宛先とする。解決できない場合は
/// [`FALLBACK_RECIPIENT`] に倒し、通知そのものは失わない（Try#3 宛先ハードコード解消）。
async fn resolve_recipient(pool: &PgPool, booking_id: &str) -> String {
    sqlx::query_scalar::<_, String>("SELECT consignee_email FROM cargo WHERE booking_id = $1")
        .bind(booking_id)
        .fetch_optional(pool)
        .await
        .ok()
        .flatten()
        .filter(|c| !c.trim().is_empty())
        .unwrap_or_else(|| FALLBACK_RECIPIENT.to_string())
}

async fn record_notification(
    pool: &PgPool,
    booking_id: &str,
    notification_type: &str,
    recipient_email: &str,
    subject: &str,
    body: &str,
) -> Result<(), String> {
    sqlx::query(
        r"INSERT INTO notification
            (booking_id, notification_type, recipient_role, recipient_email, subject, body)
          VALUES ($1, $2, $3, $4, $5, $6)",
    )
    .bind(booking_id)
    .bind(notification_type)
    .bind("ROLE_SHIPPER")
    .bind(recipient_email)
    .bind(subject)
    .bind(body)
    .execute(pool)
    .await
    .map(|_| ())
    .map_err(|e| e.to_string())
}

/// `ConfirmedBookingIssuer`（US14）: 確定予約を `TrackingIssued` へ遷移させ通知先を返す。
pub struct CargoConfirmedBookingIssuer {
    cargo_repo: Arc<dyn CargoRepository>,
}

impl CargoConfirmedBookingIssuer {
    /// アダプターを生成する。
    #[must_use]
    pub fn new(cargo_repo: Arc<dyn CargoRepository>) -> Self {
        Self { cargo_repo }
    }
}

#[async_trait]
impl ConfirmedBookingIssuer for CargoConfirmedBookingIssuer {
    async fn issue_tracking_for_booking(
        &self,
        booking_id: &str,
    ) -> Result<ConfirmedBookingInfo, TrackingServiceError> {
        let id = BookingId::parse(booking_id)
            .map_err(|_| TrackingServiceError::NotFound(booking_id.to_string()))?;
        let mut cargo = self
            .cargo_repo
            .find_by_booking_id(&id)
            .await
            .map_err(|e| TrackingServiceError::Backend(e.to_string()))?
            .ok_or_else(|| TrackingServiceError::NotFound(booking_id.to_string()))?;
        // Confirmed → TrackingIssued。既に TrackingIssued なら中間状態からの回復として
        // 遷移をスキップする（ADR-0006 冪等再操作パス）。確定前の状態は NotIssuable。
        match cargo.status() {
            domain_booking::BookingStatus::TrackingIssued => { /* 回復: 遷移不要 */ }
            _ => {
                cargo
                    .issue_tracking()
                    .map_err(|e| TrackingServiceError::NotIssuable(e.to_string()))?;
                self.cargo_repo
                    .save(&cargo)
                    .await
                    .map_err(|e| TrackingServiceError::Backend(e.to_string()))?;
            }
        }
        Ok(ConfirmedBookingInfo {
            shipper_contact: cargo.consignee().contact().to_string(),
        })
    }
}

/// `TrackingNotificationPort`（US14/US15/US17）: 通知テーブルへ記録する。
pub struct SqlxTrackingNotificationPort {
    pool: PgPool,
}

impl SqlxTrackingNotificationPort {
    /// アダプターを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl TrackingNotificationPort for SqlxTrackingNotificationPort {
    async fn notify_tracking_issued(
        &self,
        contact: &str,
        booking_id: &str,
        tracking_number: &str,
    ) -> Result<(), TrackingServiceError> {
        record_notification(
            &self.pool,
            booking_id,
            "TRACKING_NUMBER_ISSUED",
            contact,
            "追跡番号を発行しました",
            &format!(
                "予約 {booking_id} の追跡番号は {tracking_number} です。追跡ページからご確認ください。"
            ),
        )
        .await
        .map_err(TrackingServiceError::Backend)
    }

    async fn notify_status_changed(
        &self,
        booking_id: &str,
        tracking_number: &str,
        status: TrackingStatus,
    ) -> Result<(), TrackingServiceError> {
        let recipient = resolve_recipient(&self.pool, booking_id).await;
        record_notification(
            &self.pool,
            booking_id,
            "CARGO_STATUS_CHANGED",
            &recipient,
            "貨物状態が更新されました",
            &format!(
                "追跡番号 {tracking_number} の貨物状態が「{}」に更新されました。",
                status.label()
            ),
        )
        .await
        .map_err(TrackingServiceError::Backend)
    }

    async fn notify_exception_raised(
        &self,
        booking_id: &str,
        tracking_number: &str,
        description: &str,
    ) -> Result<(), TrackingServiceError> {
        let recipient = resolve_recipient(&self.pool, booking_id).await;
        record_notification(
            &self.pool,
            booking_id,
            "EXCEPTION_RAISED",
            &recipient,
            "輸送に例外が発生しました",
            &format!("追跡番号 {tracking_number} で例外が発生しました: {description}"),
        )
        .await
        .map_err(TrackingServiceError::Backend)
    }

    async fn notify_exception_resolved(
        &self,
        booking_id: &str,
        tracking_number: &str,
        resolution_notes: &str,
    ) -> Result<(), TrackingServiceError> {
        let recipient = resolve_recipient(&self.pool, booking_id).await;
        record_notification(
            &self.pool,
            booking_id,
            "EXCEPTION_RESOLVED",
            &recipient,
            "例外への対応をご報告します",
            &format!("追跡番号 {tracking_number} の例外対応: {resolution_notes}"),
        )
        .await
        .map_err(TrackingServiceError::Backend)
    }
}

/// `TrackingReflectionPort`（US15/US16）: 荷役結果を追跡へ反映し荷主へ通知する。
pub struct TrackingReflectionAdapter {
    tracking_repo: Arc<dyn TrackingActivityRepository>,
    pool: PgPool,
}

impl TrackingReflectionAdapter {
    /// アダプターを生成する。
    #[must_use]
    pub fn new(tracking_repo: Arc<dyn TrackingActivityRepository>, pool: PgPool) -> Self {
        Self {
            tracking_repo,
            pool,
        }
    }
}

#[async_trait]
impl TrackingReflectionPort for TrackingReflectionAdapter {
    async fn resolve_booking(
        &self,
        tracking_number: &str,
    ) -> Result<Option<String>, HandlingServiceError> {
        let number = TrackingNumber::parse(tracking_number)
            .map_err(|e| HandlingServiceError::InvalidInput(e.to_string()))?;
        let activity = self
            .tracking_repo
            .find_by_tracking_number(&number)
            .await
            .map_err(|e| HandlingServiceError::Backend(e.to_string()))?;
        Ok(activity.map(|a| a.booking_id().as_str().to_string()))
    }

    async fn reflect_handling(
        &self,
        tracking_number: &str,
        status: &str,
        un_locode: &str,
        event_time: DateTime<Utc>,
        voyage_number: Option<String>,
    ) -> Result<(), HandlingServiceError> {
        let number = TrackingNumber::parse(tracking_number)
            .map_err(|e| HandlingServiceError::InvalidInput(e.to_string()))?;
        let mut activity = self
            .tracking_repo
            .find_by_tracking_number(&number)
            .await
            .map_err(|e| HandlingServiceError::Backend(e.to_string()))?
            .ok_or_else(|| HandlingServiceError::TrackingNotFound(tracking_number.to_string()))?;
        let tracking_status = TrackingStatus::from_str_or_unknown(status);
        let location = TrackingLocation::new(un_locode)
            .map_err(|e| HandlingServiceError::InvalidInput(e.to_string()))?;
        activity.record_event(TrackingActivityEvent::new(
            tracking_status,
            location,
            event_time,
            voyage_number.and_then(TrackingVoyageNumber::new),
        ));
        let booking_id = activity.booking_id().as_str().to_string();
        self.tracking_repo
            .save(&activity)
            .await
            .map_err(|e| HandlingServiceError::Backend(e.to_string()))?;
        // 荷主へ状態変更を通知（記録）。
        let recipient = resolve_recipient(&self.pool, &booking_id).await;
        record_notification(
            &self.pool,
            &booking_id,
            "CARGO_STATUS_CHANGED",
            &recipient,
            "貨物状態が更新されました",
            &format!(
                "追跡番号 {tracking_number} の貨物状態が「{}」に更新されました。",
                tracking_status.label()
            ),
        )
        .await
        .map_err(HandlingServiceError::Backend)
    }
}

/// `RouteCheckPort`（US15）: 作業場所が確定経路上かを判定する（簡易・非ブロッキング警告）。
///
/// 確定経路の経由港・端点に作業場所が含まれれば「ルート上」とみなす。確定経路が無い場合は
/// 警告を出さない（ルート未確定のため判定不能）。詳細照合は後続 IT（計画注記）。
pub struct SelectedRouteCheckAdapter {
    selected_route_view: Arc<dyn SelectedRouteView>,
}

impl SelectedRouteCheckAdapter {
    /// アダプターを生成する。
    #[must_use]
    pub fn new(selected_route_view: Arc<dyn SelectedRouteView>) -> Self {
        Self {
            selected_route_view,
        }
    }
}

#[async_trait]
impl RouteCheckPort for SelectedRouteCheckAdapter {
    async fn check_route(
        &self,
        booking_id: &str,
        un_locode: &str,
    ) -> Result<RouteCheck, HandlingServiceError> {
        let summary = self
            .selected_route_view
            .find_by_booking(booking_id)
            .await
            .map_err(|e| HandlingServiceError::Backend(e.to_string()))?;
        match summary {
            // 確定経路が無ければ判定不能（警告しない）。
            None => Ok(RouteCheck::Unknown),
            Some(route) => {
                if route.transit_ports.iter().any(|p| p == un_locode) {
                    Ok(RouteCheck::OnRoute)
                } else {
                    Ok(RouteCheck::OffRoute)
                }
            }
        }
    }
}
