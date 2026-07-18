//! Shipper Context の出力ポート（trait）。実装はインフラ層で行う。

use crate::aggregate::Shipper;
use crate::value_objects::Email;
use shared_kernel::ShipperId;

/// リポジトリ操作のエラー。インフラ層の詳細に依存しない抽象エラー。
#[derive(Debug, thiserror::Error)]
pub enum RepositoryError {
    /// 永続化層で発生したエラー。
    #[error("repository failure: {0}")]
    Backend(String),
}

/// 荷主リポジトリの出力ポート。
#[async_trait::async_trait]
pub trait ShipperRepository: Send + Sync {
    /// 荷主を永続化する。
    async fn save(&self, shipper: &Shipper) -> Result<(), RepositoryError>;

    /// 荷主 ID で検索する。
    async fn find_by_id(&self, id: &ShipperId) -> Result<Option<Shipper>, RepositoryError>;

    /// 指定メールアドレスの荷主が既に存在するかを返す。
    async fn exists_by_email(&self, email: &Email) -> Result<bool, RepositoryError>;
}
