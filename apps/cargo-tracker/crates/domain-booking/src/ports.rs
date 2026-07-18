//! Booking Context の出力ポート（trait）。実装はインフラ層で行う。

use crate::aggregate::Cargo;
use crate::value_objects::BookingId;
use shared_kernel::ShipperId;

/// リポジトリ操作のエラー。
#[derive(Debug, thiserror::Error)]
pub enum RepositoryError {
    /// 永続化層で発生したエラー。
    #[error("repository failure: {0}")]
    Backend(String),
}

/// ACL（腐敗防止層）操作のエラー。
#[derive(Debug, thiserror::Error)]
pub enum AclError {
    /// 外部コンテキスト参照時のエラー。
    #[error("acl failure: {0}")]
    Backend(String),
}

/// 貨物リポジトリの出力ポート。
#[async_trait::async_trait]
pub trait CargoRepository: Send + Sync {
    /// 貨物を永続化する。
    async fn save(&self, cargo: &Cargo) -> Result<(), RepositoryError>;

    /// 予約 ID で検索する。
    async fn find_by_booking_id(&self, id: &BookingId) -> Result<Option<Cargo>, RepositoryError>;
}

/// Shipper Context への ACL ポート。荷主の存在を確認する。
///
/// Booking Context は Shipper Context を直接参照せず、この trait を通じてのみ問い合わせる。
#[async_trait::async_trait]
pub trait ShipperExistenceChecker: Send + Sync {
    /// 指定荷主 ID が存在するかを返す。
    async fn exists(&self, shipper_id: &ShipperId) -> Result<bool, AclError>;
}
