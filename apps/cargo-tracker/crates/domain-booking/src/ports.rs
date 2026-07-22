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

/// 確定経路の読み取り要約（荷主通知 US12 の通知内容組み立てに使用）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SelectedRouteSummary {
    /// 航海番号列（区間順）。
    pub voyage_numbers: Vec<String>,
    /// 経由港の UN/LOCODE 列（出発地→各積替港→目的地）。
    pub transit_ports: Vec<String>,
    /// 所要日数（最初の積載から最後の荷揚までの日数）。
    pub transit_days: i64,
    /// 到着予定日（最後の区間の荷揚日、`YYYY-MM-DD`）。
    pub expected_arrival: String,
}

/// Routing Context への逆方向 ACL ポート。確定経路の要約を読み取る（US11/US12）。
///
/// Booking Context は Routing Context を直接参照せず、この trait を通じてのみ確定経路を得る
/// （IT3 の `CargoSpecProvider` と対をなす逆方向 ACL）。
#[async_trait::async_trait]
pub trait SelectedRouteView: Send + Sync {
    /// 予約番号に紐づく確定経路の要約を返す（未確定なら `None`）。
    async fn find_by_booking(
        &self,
        booking_id: &str,
    ) -> Result<Option<SelectedRouteSummary>, AclError>;
}

/// `Arc<dyn CargoRepository>` へ委譲するブランケット実装（ADR-0003）。
#[async_trait::async_trait]
impl CargoRepository for std::sync::Arc<dyn CargoRepository> {
    async fn save(&self, cargo: &Cargo) -> Result<(), RepositoryError> {
        (**self).save(cargo).await
    }
    async fn find_by_booking_id(&self, id: &BookingId) -> Result<Option<Cargo>, RepositoryError> {
        (**self).find_by_booking_id(id).await
    }
}

/// `Arc<dyn ShipperExistenceChecker>` へ委譲するブランケット実装（ADR-0003）。
#[async_trait::async_trait]
impl ShipperExistenceChecker for std::sync::Arc<dyn ShipperExistenceChecker> {
    async fn exists(&self, shipper_id: &ShipperId) -> Result<bool, AclError> {
        (**self).exists(shipper_id).await
    }
}

/// `Arc<dyn SelectedRouteView>` へ委譲するブランケット実装（ADR-0003）。
#[async_trait::async_trait]
impl SelectedRouteView for std::sync::Arc<dyn SelectedRouteView> {
    async fn find_by_booking(
        &self,
        booking_id: &str,
    ) -> Result<Option<SelectedRouteSummary>, AclError> {
        (**self).find_by_booking(booking_id).await
    }
}
