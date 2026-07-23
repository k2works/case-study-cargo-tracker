//! Tracking Context のアプリケーション層。
//!
//! 追跡番号発行（US14）・貨物状態手動更新（US17）のユースケースを提供する。
//! Booking→Tracking の連携は ACL ポート（`ConfirmedBookingIssuer`）経由で行い、
//! `domain-booking` への直接依存を持たない（BC 独立・ADR-0004 の逐次書き込み）。

use async_trait::async_trait;
use domain_tracking::{
    TrackingActivity, TrackingActivityEvent, TrackingActivityRepository, TrackingBookingId,
    TrackingLocation, TrackingNumber, TrackingNumberGenerator, TrackingRepositoryError,
    TrackingStatus, TrackingVoyageNumber,
};

/// 追跡ユースケースのエラー。
#[derive(Debug, thiserror::Error)]
pub enum TrackingServiceError {
    /// 対象の予約または追跡が見つからない。
    #[error("対象が見つかりません: {0}")]
    NotFound(String),
    /// 予約が確定状態でない等、業務前提を満たさない。
    #[error("追跡番号を発行できません: {0}")]
    NotIssuable(String),
    /// 入力値が不正。
    #[error("入力が不正です: {0}")]
    InvalidInput(String),
    /// 永続化・外部連携の失敗。
    #[error("処理に失敗しました: {0}")]
    Backend(String),
}

impl From<TrackingRepositoryError> for TrackingServiceError {
    fn from(e: TrackingRepositoryError) -> Self {
        Self::Backend(e.to_string())
    }
}

/// 確定予約情報（追跡発行時に Booking 側から取得する ACL の戻り値）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ConfirmedBookingInfo {
    /// 荷主（荷受人）への通知先。
    pub shipper_contact: String,
}

/// Booking→Tracking の追跡発行 ACL（Tracking 側ポート）。
///
/// 実装は composition root で `domain-booking`／`app-booking` をラップし、
/// 予約が確定状態であることを検証したうえで `Confirmed → TrackingIssued` に遷移させ、
/// 通知先などの情報を返す（ADR-0004: BC 跨ぎ書き込みは Booking 側を先に確定）。
#[async_trait]
pub trait ConfirmedBookingIssuer: Send + Sync {
    /// 予約を追跡番号発行済へ遷移させ、確定予約情報を返す。
    async fn issue_tracking_for_booking(
        &self,
        booking_id: &str,
    ) -> Result<ConfirmedBookingInfo, TrackingServiceError>;
}

/// 追跡通知ポート（送信＝記録・US14/US15/US17）。
#[async_trait]
pub trait TrackingNotificationPort: Send + Sync {
    /// 追跡番号発行を荷主へ通知する（US14）。
    async fn notify_tracking_issued(
        &self,
        contact: &str,
        booking_id: &str,
        tracking_number: &str,
    ) -> Result<(), TrackingServiceError>;

    /// 貨物状態変更を荷主へ通知する（US15/US17）。
    async fn notify_status_changed(
        &self,
        booking_id: &str,
        tracking_number: &str,
        status: TrackingStatus,
    ) -> Result<(), TrackingServiceError>;
}

/// 追跡番号発行ユースケース（US14）。
pub struct IssueTrackingService<R, G, B, N>
where
    R: TrackingActivityRepository,
    G: TrackingNumberGenerator,
    B: ConfirmedBookingIssuer,
    N: TrackingNotificationPort,
{
    repository: R,
    generator: G,
    booking: B,
    notifications: N,
}

impl<R, G, B, N> IssueTrackingService<R, G, B, N>
where
    R: TrackingActivityRepository,
    G: TrackingNumberGenerator,
    B: ConfirmedBookingIssuer,
    N: TrackingNotificationPort,
{
    /// サービスを生成する。
    pub fn new(repository: R, generator: G, booking: B, notifications: N) -> Self {
        Self {
            repository,
            generator,
            booking,
            notifications,
        }
    }

    /// 確定予約に対して追跡番号を発行する（US14・冪等・ADR-0006）。
    ///
    /// 手順（ADR-0004 逐次書き込み・ADR-0006 冪等回復）:
    /// 0. 予約に対する追跡活動が既に存在すれば、その追跡番号をそのまま返す（冪等・二重発行防止）
    /// 1. Booking 側で確定検証 → `TrackingIssued` へ遷移（ACL。既に TrackingIssued なら回復として許容）
    /// 2. 追跡番号を採番し `TrackingActivity`（受領待ち）を生成・保存
    /// 3. 荷主へ追跡番号を通知（記録）
    ///
    /// 中間状態（予約 TrackingIssued・追跡レコード無し）からの再実行は、手順 0 で追跡が無く
    /// 手順 1 で Booking が既に遷移済みのため、追跡レコード生成・通知から回復して収束する。
    ///
    /// # Errors
    ///
    /// 予約が無い/未確定なら `NotFound`/`NotIssuable`、永続化・通知失敗は `Backend`。
    pub async fn issue_tracking(
        &self,
        booking_id: &str,
    ) -> Result<TrackingNumber, TrackingServiceError> {
        // 冪等: 既に追跡活動があれば新規発行せず既存番号を返す（二重発行防止）。
        if let Some(existing) = self.repository.find_by_booking_id(booking_id).await? {
            return Ok(existing.tracking_number().clone());
        }
        let info = self.booking.issue_tracking_for_booking(booking_id).await?;
        let booking_ref = TrackingBookingId::parse(booking_id)
            .map_err(|e| TrackingServiceError::InvalidInput(e.to_string()))?;
        let number = self.generator.generate();
        let activity = TrackingActivity::issue(number.clone(), booking_ref);
        self.repository.save(&activity).await?;
        self.notifications
            .notify_tracking_issued(&info.shipper_contact, booking_id, number.as_str())
            .await?;
        Ok(number)
    }
}

/// 貨物状態手動更新の入力（US17）。
#[derive(Debug, Clone)]
pub struct ManualStatusUpdate {
    /// 新しい輸送状態。
    pub status: TrackingStatus,
    /// 位置（UN/LOCODE）。
    pub un_locode: String,
    /// 更新日時。
    pub event_time: chrono::DateTime<chrono::Utc>,
    /// 航海番号（任意）。
    pub voyage_number: Option<String>,
}

/// 貨物状態手動更新ユースケース（US17）。
pub struct ManualTrackingUpdateService<R, N>
where
    R: TrackingActivityRepository,
    N: TrackingNotificationPort,
{
    repository: R,
    notifications: N,
}

impl<R, N> ManualTrackingUpdateService<R, N>
where
    R: TrackingActivityRepository,
    N: TrackingNotificationPort,
{
    /// サービスを生成する。
    pub fn new(repository: R, notifications: N) -> Self {
        Self {
            repository,
            notifications,
        }
    }

    /// 追跡番号を指定して状態・位置・日時を手動更新する（US17）。
    ///
    /// # Errors
    ///
    /// 追跡が無ければ `NotFound`、位置が不正なら `InvalidInput`、永続化・通知失敗は `Backend`。
    pub async fn update_status(
        &self,
        tracking_number: &str,
        update: ManualStatusUpdate,
    ) -> Result<(), TrackingServiceError> {
        let number = TrackingNumber::parse(tracking_number)
            .map_err(|e| TrackingServiceError::InvalidInput(e.to_string()))?;
        let mut activity = self
            .repository
            .find_by_tracking_number(&number)
            .await?
            .ok_or_else(|| TrackingServiceError::NotFound(tracking_number.to_string()))?;
        let location = TrackingLocation::new(&update.un_locode)
            .map_err(|e| TrackingServiceError::InvalidInput(e.to_string()))?;
        let voyage = update.voyage_number.and_then(TrackingVoyageNumber::new);
        activity.record_event(TrackingActivityEvent::new(
            update.status,
            location,
            update.event_time,
            voyage,
        ));
        self.repository.save(&activity).await?;
        self.notifications
            .notify_status_changed(
                activity.booking_id().as_str(),
                tracking_number,
                update.status,
            )
            .await?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use domain_tracking::UuidTrackingNumberGenerator;
    use mockall::mock;
    use mockall::predicate::*;

    mock! {
        Repo {}
        #[async_trait]
        impl TrackingActivityRepository for Repo {
            async fn save(&self, activity: &TrackingActivity) -> Result<(), TrackingRepositoryError>;
            async fn find_by_tracking_number(
                &self,
                number: &TrackingNumber,
            ) -> Result<Option<TrackingActivity>, TrackingRepositoryError>;
            async fn find_by_booking_id(
                &self,
                booking_id: &str,
            ) -> Result<Option<TrackingActivity>, TrackingRepositoryError>;
        }
    }

    mock! {
        Booking {}
        #[async_trait]
        impl ConfirmedBookingIssuer for Booking {
            async fn issue_tracking_for_booking(
                &self,
                booking_id: &str,
            ) -> Result<ConfirmedBookingInfo, TrackingServiceError>;
        }
    }

    mock! {
        Notify {}
        #[async_trait]
        impl TrackingNotificationPort for Notify {
            async fn notify_tracking_issued(
                &self,
                contact: &str,
                booking_id: &str,
                tracking_number: &str,
            ) -> Result<(), TrackingServiceError>;
            async fn notify_status_changed(
                &self,
                booking_id: &str,
                tracking_number: &str,
                status: TrackingStatus,
            ) -> Result<(), TrackingServiceError>;
        }
    }

    #[tokio::test]
    async fn 確定予約に追跡番号を発行できる() {
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id()
            .times(1)
            .returning(|_| Ok(None));
        repo.expect_save().times(1).returning(|_| Ok(()));
        let mut booking = MockBooking::new();
        booking
            .expect_issue_tracking_for_booking()
            .with(eq("BKG-1"))
            .times(1)
            .returning(|_| {
                Ok(ConfirmedBookingInfo {
                    shipper_contact: "shipper@example.com".to_string(),
                })
            });
        let mut notify = MockNotify::new();
        notify
            .expect_notify_tracking_issued()
            .times(1)
            .returning(|_, _, _| Ok(()));

        let service = IssueTrackingService::new(repo, UuidTrackingNumberGenerator, booking, notify);
        let number = service
            .issue_tracking("BKG-1")
            .await
            .expect("発行できるはず");
        assert!(number.as_str().starts_with("TRK-"));
    }

    #[tokio::test]
    async fn 未確定予約への追跡発行は拒否される() {
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id().returning(|_| Ok(None));
        repo.expect_save().never();
        let mut booking = MockBooking::new();
        booking
            .expect_issue_tracking_for_booking()
            .returning(|_| Err(TrackingServiceError::NotIssuable("未確定".to_string())));
        let mut notify = MockNotify::new();
        notify.expect_notify_tracking_issued().never();

        let service = IssueTrackingService::new(repo, UuidTrackingNumberGenerator, booking, notify);
        let result = service.issue_tracking("BKG-1").await;
        assert!(matches!(result, Err(TrackingServiceError::NotIssuable(_))));
    }

    #[tokio::test]
    async fn 既に追跡がある予約への再発行は既存番号を返し二重発行しない() {
        // ADR-0006 冪等: 追跡活動が既にあれば新規発行せず既存番号を返す。
        let existing = TrackingActivity::issue(
            TrackingNumber::parse("TRK-EXIST").unwrap(),
            TrackingBookingId::parse("BKG-1").unwrap(),
        );
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id()
            .times(1)
            .returning(move |_| Ok(Some(existing.clone())));
        repo.expect_save().never();
        let mut booking = MockBooking::new();
        // Booking 側の遷移は呼ばれない（冪等ショートサーキット）。
        booking.expect_issue_tracking_for_booking().never();
        let mut notify = MockNotify::new();
        notify.expect_notify_tracking_issued().never();

        let service = IssueTrackingService::new(repo, UuidTrackingNumberGenerator, booking, notify);
        let number = service
            .issue_tracking("BKG-1")
            .await
            .expect("既存番号を返す");
        assert_eq!(number.as_str(), "TRK-EXIST");
    }

    #[tokio::test]
    async fn 手動更新で状態と履歴が記録され通知される() {
        let number = TrackingNumber::parse("TRK-1").unwrap();
        let existing =
            TrackingActivity::issue(number.clone(), TrackingBookingId::parse("BKG-1").unwrap());
        let mut repo = MockRepo::new();
        repo.expect_find_by_tracking_number()
            .times(1)
            .returning(move |_| Ok(Some(existing.clone())));
        repo.expect_save()
            .times(1)
            .withf(|a| {
                a.current_status() == TrackingStatus::OnboardCarrier && a.events().len() == 1
            })
            .returning(|_| Ok(()));
        let mut notify = MockNotify::new();
        notify
            .expect_notify_status_changed()
            .times(1)
            .returning(|_, _, _| Ok(()));

        let service = ManualTrackingUpdateService::new(repo, notify);
        service
            .update_status(
                "TRK-1",
                ManualStatusUpdate {
                    status: TrackingStatus::OnboardCarrier,
                    un_locode: "JPTYO".to_string(),
                    event_time: chrono::DateTime::from_timestamp(1_700_000_000, 0).unwrap(),
                    voyage_number: Some("V001".to_string()),
                },
            )
            .await
            .expect("更新できるはず");
    }

    #[tokio::test]
    async fn 存在しない追跡番号の手動更新はnotfound() {
        let mut repo = MockRepo::new();
        repo.expect_find_by_tracking_number()
            .returning(|_| Ok(None));
        repo.expect_save().never();
        let mut notify = MockNotify::new();
        notify.expect_notify_status_changed().never();

        let service = ManualTrackingUpdateService::new(repo, notify);
        let result = service
            .update_status(
                "TRK-UNKNOWN",
                ManualStatusUpdate {
                    status: TrackingStatus::Unloaded,
                    un_locode: "JPTYO".to_string(),
                    event_time: chrono::DateTime::from_timestamp(1_700_000_000, 0).unwrap(),
                    voyage_number: None,
                },
            )
            .await;
        assert!(matches!(result, Err(TrackingServiceError::NotFound(_))));
    }
}
