//! Handling Context のアプリケーション層。
//!
//! 荷役記録（US15）・引取記録（US16）のユースケースを提供する。追跡番号で貨物を特定し、
//! 荷役を記録したうえで結果としての輸送状態を Tracking へ反映する。Tracking への反映は
//! ACL ポート（`TrackingReflectionPort`）経由で行い、`domain-tracking` に直接依存しない
//! （BC 独立・ADR-0004 の逐次書き込み: Handling を先に確定）。

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use domain_handling::{
    HandlingActivity, HandlingActivityRepository, HandlingError, HandlingLocation,
    HandlingRepositoryError, HandlingType, ReceiptConfirmation,
};

/// 荷役ユースケースのエラー。
#[derive(Debug, thiserror::Error)]
pub enum HandlingServiceError {
    /// 追跡番号に対応する貨物が見つからない。
    #[error("追跡番号が存在しません: {0}")]
    TrackingNotFound(String),
    /// ドメイン不変条件違反（引取に荷受人確認が無い等）。
    #[error("荷役を記録できません: {0}")]
    Domain(#[from] HandlingError),
    /// 入力値が不正。
    #[error("入力が不正です: {0}")]
    InvalidInput(String),
    /// 永続化・外部連携の失敗。
    #[error("処理に失敗しました: {0}")]
    Backend(String),
}

impl From<HandlingRepositoryError> for HandlingServiceError {
    fn from(e: HandlingRepositoryError) -> Self {
        Self::Backend(e.to_string())
    }
}

/// Handling→Tracking 反映 ACL（Handling 側ポート）。
///
/// 追跡番号から予約を解決し、荷役結果としての輸送状態を追跡へ追記する。実装は composition root で
/// `domain-tracking`／`app-tracking` をラップする（BC 独立）。
#[async_trait]
pub trait TrackingReflectionPort: Send + Sync {
    /// 追跡番号から予約 ID を解決する。存在しなければ `None`。
    async fn resolve_booking(
        &self,
        tracking_number: &str,
    ) -> Result<Option<String>, HandlingServiceError>;

    /// 荷役結果の輸送状態を追跡へ反映し、荷主へ状態変更を通知する（記録）。
    ///
    /// `status` は `HandlingType::resulting_transport_status`（SCREAMING_SNAKE_CASE）。
    async fn reflect_handling(
        &self,
        tracking_number: &str,
        status: &str,
        un_locode: &str,
        event_time: DateTime<Utc>,
        voyage_number: Option<String>,
    ) -> Result<(), HandlingServiceError>;
}

/// 予定ルート照合の結果（IT5 Try#5）。
///
/// `bool` では「ルート上」と「判定不能（確定経路が無い等）」が区別できず警告抑止の意味が
/// 混在していたため、3 値に分離した。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RouteCheck {
    /// 作業場所が予定ルート上にある。
    OnRoute,
    /// 作業場所が予定ルート外にある（警告対象）。
    OffRoute,
    /// 予定ルートが未確定などで判定できない（警告しない）。
    Unknown,
}

/// 予定ルート照合 ACL（作業場所が予定ルート上かを判定・US15 警告）。
#[async_trait]
pub trait RouteCheckPort: Send + Sync {
    /// 作業場所が予約の予定ルート上にあるかを判定する。
    async fn check_route(
        &self,
        booking_id: &str,
        un_locode: &str,
    ) -> Result<RouteCheck, HandlingServiceError>;
}

/// 荷役記録の入力（US15/US16）。
#[derive(Debug, Clone)]
pub struct RecordHandlingInput {
    /// 追跡番号（貨物特定）。
    pub tracking_number: String,
    /// 荷役種別。
    pub handling_type: HandlingType,
    /// 作業完了日時。
    pub completion_time: DateTime<Utc>,
    /// 作業場所（UN/LOCODE）。
    pub un_locode: String,
    /// 航海番号（任意）。
    pub voyage_number: Option<String>,
    /// 作業者名（任意）。
    pub operator_name: Option<String>,
    /// 荷受人確認（引取時に必須）。
    pub receipt_confirmation: Option<String>,
}

/// 荷役記録の結果。ルート相違警告を含む（非ブロッキング）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RecordHandlingOutcome {
    /// 予定ルートと異なる場所の場合の警告メッセージ。
    pub route_warning: Option<String>,
}

/// 荷役記録ユースケース（US15/US16）。
pub struct RecordHandlingService<R, T, C>
where
    R: HandlingActivityRepository,
    T: TrackingReflectionPort,
    C: RouteCheckPort,
{
    repository: R,
    tracking: T,
    route_check: C,
}

impl<R, T, C> RecordHandlingService<R, T, C>
where
    R: HandlingActivityRepository,
    T: TrackingReflectionPort,
    C: RouteCheckPort,
{
    /// サービスを生成する。
    pub fn new(repository: R, tracking: T, route_check: C) -> Self {
        Self {
            repository,
            tracking,
            route_check,
        }
    }

    /// 荷役作業を記録する（US15/US16）。
    ///
    /// 手順（ADR-0004・逐次書き込み）:
    /// 1. 追跡番号から予約を解決（存在しなければ `TrackingNotFound`）
    /// 2. `HandlingActivity` を生成（引取は荷受人確認必須の不変条件）
    /// 3. 荷役記録を保存（Handling を先に確定）
    /// 4. 結果の輸送状態を Tracking へ反映＋荷主へ状態変更通知
    /// 5. 作業場所が予定ルート外なら警告を返す（非ブロッキング）
    ///
    /// # Errors
    ///
    /// 追跡番号不明は `TrackingNotFound`、不変条件違反は `Domain`、位置不正は `InvalidInput`。
    pub async fn record(
        &self,
        input: RecordHandlingInput,
    ) -> Result<RecordHandlingOutcome, HandlingServiceError> {
        let booking_id = self
            .tracking
            .resolve_booking(&input.tracking_number)
            .await?
            .ok_or_else(|| HandlingServiceError::TrackingNotFound(input.tracking_number.clone()))?;

        let location = HandlingLocation::new(&input.un_locode)
            .map_err(|e| HandlingServiceError::InvalidInput(e.to_string()))?;
        let receipt = match input.receipt_confirmation.as_deref() {
            Some(v) if !v.trim().is_empty() => Some(
                ReceiptConfirmation::new(v)
                    .map_err(|e| HandlingServiceError::InvalidInput(e.to_string()))?,
            ),
            _ => None,
        };

        let activity = HandlingActivity::register(
            booking_id.clone(),
            input.handling_type,
            input.completion_time,
            location,
            input.voyage_number.clone(),
            input.operator_name.clone(),
            receipt,
        )?;
        self.repository.save(&activity).await?;

        self.tracking
            .reflect_handling(
                &input.tracking_number,
                input.handling_type.resulting_transport_status(),
                &input.un_locode,
                input.completion_time,
                input.voyage_number.clone(),
            )
            .await?;

        // OffRoute のみ警告。OnRoute・Unknown（判定不能）は警告しない。
        let route_warning = match self
            .route_check
            .check_route(&booking_id, &input.un_locode)
            .await?
        {
            RouteCheck::OffRoute => Some(format!(
                "作業場所 {} は予定ルート上にありません。ルートを確認してください。",
                input.un_locode
            )),
            RouteCheck::OnRoute | RouteCheck::Unknown => None,
        };

        Ok(RecordHandlingOutcome { route_warning })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use mockall::mock;
    use mockall::predicate::*;

    mock! {
        Repo {}
        #[async_trait]
        impl HandlingActivityRepository for Repo {
            async fn save(&self, activity: &HandlingActivity) -> Result<(), HandlingRepositoryError>;
            async fn find_by_booking_id(
                &self,
                booking_id: &str,
            ) -> Result<Vec<HandlingActivity>, HandlingRepositoryError>;
        }
    }

    mock! {
        Tracking {}
        #[async_trait]
        impl TrackingReflectionPort for Tracking {
            async fn resolve_booking(
                &self,
                tracking_number: &str,
            ) -> Result<Option<String>, HandlingServiceError>;
            async fn reflect_handling(
                &self,
                tracking_number: &str,
                status: &str,
                un_locode: &str,
                event_time: DateTime<Utc>,
                voyage_number: Option<String>,
            ) -> Result<(), HandlingServiceError>;
        }
    }

    mock! {
        Route {}
        #[async_trait]
        impl RouteCheckPort for Route {
            async fn check_route(
                &self,
                booking_id: &str,
                un_locode: &str,
            ) -> Result<RouteCheck, HandlingServiceError>;
        }
    }

    fn now() -> DateTime<Utc> {
        DateTime::from_timestamp(1_700_000_000, 0).unwrap()
    }

    fn input(handling_type: HandlingType, receipt: Option<&str>) -> RecordHandlingInput {
        RecordHandlingInput {
            tracking_number: "TRK-1".to_string(),
            handling_type,
            completion_time: now(),
            un_locode: "JPTYO".to_string(),
            voyage_number: Some("V001".to_string()),
            operator_name: Some("作業員A".to_string()),
            receipt_confirmation: receipt.map(str::to_string),
        }
    }

    #[tokio::test]
    async fn 追跡番号で貨物を特定して荷役を記録し追跡へ反映する() {
        let mut repo = MockRepo::new();
        repo.expect_save().times(1).returning(|_| Ok(()));
        let mut tracking = MockTracking::new();
        tracking
            .expect_resolve_booking()
            .with(eq("TRK-1"))
            .returning(|_| Ok(Some("BKG-1".to_string())));
        tracking
            .expect_reflect_handling()
            .withf(|_, status, _, _, _| status == "RECEIVED")
            .times(1)
            .returning(|_, _, _, _, _| Ok(()));
        let mut route = MockRoute::new();
        route
            .expect_check_route()
            .returning(|_, _| Ok(RouteCheck::OnRoute));

        let service = RecordHandlingService::new(repo, tracking, route);
        let outcome = service
            .record(input(HandlingType::Receive, None))
            .await
            .expect("記録できるはず");
        assert!(outcome.route_warning.is_none());
    }

    #[tokio::test]
    async fn 存在しない追跡番号はエラー() {
        let mut repo = MockRepo::new();
        repo.expect_save().never();
        let mut tracking = MockTracking::new();
        tracking.expect_resolve_booking().returning(|_| Ok(None));
        tracking.expect_reflect_handling().never();
        let mut route = MockRoute::new();
        route.expect_check_route().never();

        let service = RecordHandlingService::new(repo, tracking, route);
        let result = service.record(input(HandlingType::Load, None)).await;
        assert!(matches!(
            result,
            Err(HandlingServiceError::TrackingNotFound(_))
        ));
    }

    #[tokio::test]
    async fn 引取は荷受人確認がないと記録できない() {
        let mut repo = MockRepo::new();
        repo.expect_save().never();
        let mut tracking = MockTracking::new();
        tracking
            .expect_resolve_booking()
            .returning(|_| Ok(Some("BKG-1".to_string())));
        tracking.expect_reflect_handling().never();
        let mut route = MockRoute::new();
        route.expect_check_route().never();

        let service = RecordHandlingService::new(repo, tracking, route);
        let result = service.record(input(HandlingType::Claim, None)).await;
        assert!(matches!(
            result,
            Err(HandlingServiceError::Domain(
                HandlingError::ReceiptConfirmationRequired
            ))
        ));
    }

    #[tokio::test]
    async fn 引取は荷受人確認があれば記録できる() {
        let mut repo = MockRepo::new();
        repo.expect_save().times(1).returning(|_| Ok(()));
        let mut tracking = MockTracking::new();
        tracking
            .expect_resolve_booking()
            .returning(|_| Ok(Some("BKG-1".to_string())));
        tracking
            .expect_reflect_handling()
            .withf(|_, status, _, _, _| status == "CLAIMED")
            .returning(|_, _, _, _, _| Ok(()));
        let mut route = MockRoute::new();
        route
            .expect_check_route()
            .returning(|_, _| Ok(RouteCheck::OnRoute));

        let service = RecordHandlingService::new(repo, tracking, route);
        let outcome = service
            .record(input(HandlingType::Claim, Some("署名: 荷受 太郎")))
            .await
            .expect("記録できるはず");
        assert!(outcome.route_warning.is_none());
    }

    #[tokio::test]
    async fn 予定ルート外の場所は警告を返す() {
        let mut repo = MockRepo::new();
        repo.expect_save().returning(|_| Ok(()));
        let mut tracking = MockTracking::new();
        tracking
            .expect_resolve_booking()
            .returning(|_| Ok(Some("BKG-1".to_string())));
        tracking
            .expect_reflect_handling()
            .returning(|_, _, _, _, _| Ok(()));
        let mut route = MockRoute::new();
        route
            .expect_check_route()
            .returning(|_, _| Ok(RouteCheck::OffRoute));

        let service = RecordHandlingService::new(repo, tracking, route);
        let outcome = service
            .record(input(HandlingType::Load, None))
            .await
            .expect("記録できるはず");
        assert!(outcome.route_warning.is_some());
    }
}
