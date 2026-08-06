//! Handling Context の出力ポート trait。

use crate::aggregate::HandlingActivity;

/// 永続化エラー（インフラ実装が詳細を隠蔽する）。
#[derive(Debug, thiserror::Error)]
pub enum HandlingRepositoryError {
    /// 永続化層のエラー。
    #[error("荷役記録の永続化に失敗しました: {0}")]
    Storage(String),
}

/// 荷役作業リポジトリ（出力ポート）。
#[async_trait::async_trait]
pub trait HandlingActivityRepository: Send + Sync {
    /// 荷役作業を保存する。
    async fn save(&self, activity: &HandlingActivity) -> Result<(), HandlingRepositoryError>;

    /// 予約 ID に紐づく荷役作業履歴を新しい順に取得する（Read Model 用）。
    async fn find_by_booking_id(
        &self,
        booking_id: &str,
    ) -> Result<Vec<HandlingActivity>, HandlingRepositoryError>;
}

/// `Arc<dyn HandlingActivityRepository>` へ委譲するブランケット実装（ADR-0003）。
#[async_trait::async_trait]
impl HandlingActivityRepository for std::sync::Arc<dyn HandlingActivityRepository> {
    async fn save(&self, activity: &HandlingActivity) -> Result<(), HandlingRepositoryError> {
        (**self).save(activity).await
    }
    async fn find_by_booking_id(
        &self,
        booking_id: &str,
    ) -> Result<Vec<HandlingActivity>, HandlingRepositoryError> {
        (**self).find_by_booking_id(booking_id).await
    }
}
