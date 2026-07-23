//! Estimation Context の出力ポート trait。

use crate::aggregate::Estimate;
use crate::value_objects::EstimateId;

/// 永続化エラー。
#[derive(Debug, thiserror::Error)]
pub enum EstimationRepositoryError {
    /// 永続化層のエラー。
    #[error("見積の永続化に失敗しました: {0}")]
    Storage(String),
}

/// 見積リポジトリ（出力ポート）。
#[async_trait::async_trait]
pub trait EstimateRepository: Send + Sync {
    /// 見積を保存する（新規・更新兼用）。
    async fn save(&self, estimate: &Estimate) -> Result<(), EstimationRepositoryError>;

    /// 見積 ID で検索する。
    async fn find_by_id(
        &self,
        id: &EstimateId,
    ) -> Result<Option<Estimate>, EstimationRepositoryError>;

    /// 全見積を新しい順に取得する（一覧・Read Model）。
    async fn find_all(&self) -> Result<Vec<Estimate>, EstimationRepositoryError>;
}
