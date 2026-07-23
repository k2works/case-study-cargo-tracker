//! Tracking Context の出力ポート trait。

use crate::aggregate::TrackingActivity;
use crate::value_objects::TrackingNumber;

/// 永続化エラー（インフラ実装が詳細を隠蔽する）。
#[derive(Debug, thiserror::Error)]
pub enum TrackingRepositoryError {
    /// 永続化層のエラー。
    #[error("追跡の永続化に失敗しました: {0}")]
    Storage(String),
}

/// 追跡活動リポジトリ（出力ポート）。
#[async_trait::async_trait]
pub trait TrackingActivityRepository: Send + Sync {
    /// 追跡活動を保存する（新規・更新兼用）。
    async fn save(&self, activity: &TrackingActivity) -> Result<(), TrackingRepositoryError>;

    /// 追跡番号で追跡活動を検索する。
    async fn find_by_tracking_number(
        &self,
        number: &TrackingNumber,
    ) -> Result<Option<TrackingActivity>, TrackingRepositoryError>;
}

/// `Arc<dyn TrackingActivityRepository>` へ委譲するブランケット実装（ADR-0003）。
#[async_trait::async_trait]
impl TrackingActivityRepository for std::sync::Arc<dyn TrackingActivityRepository> {
    async fn save(&self, activity: &TrackingActivity) -> Result<(), TrackingRepositoryError> {
        (**self).save(activity).await
    }
    async fn find_by_tracking_number(
        &self,
        number: &TrackingNumber,
    ) -> Result<Option<TrackingActivity>, TrackingRepositoryError> {
        (**self).find_by_tracking_number(number).await
    }
}

/// 追跡番号採番ポート（出力ポート）。一意な追跡番号の発行を担う（US14）。
pub trait TrackingNumberGenerator: Send + Sync {
    /// 新しい追跡番号を採番する。
    fn generate(&self) -> TrackingNumber;
}

/// UUID ベースの既定採番実装（ドメイン内で完結する純粋な採番）。
#[derive(Debug, Default, Clone, Copy)]
pub struct UuidTrackingNumberGenerator;

impl TrackingNumberGenerator for UuidTrackingNumberGenerator {
    fn generate(&self) -> TrackingNumber {
        TrackingNumber::generate()
    }
}
