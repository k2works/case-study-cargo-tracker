//! Routing Context の出力ポート（trait）。実装はインフラ層で行う。

use crate::aggregate::Voyage;
use crate::value_objects::{CargoType, VoyageNumber};
use chrono::NaiveDate;
use shared_kernel::Location;

/// リポジトリ操作のエラー。インフラ層の詳細に依存しない抽象エラー。
#[derive(Debug, thiserror::Error)]
pub enum RepositoryError {
    /// 永続化層で発生したエラー。
    #[error("repository failure: {0}")]
    Backend(String),
}

/// 航海スケジュール検索の条件（US07）。
///
/// いずれのフィールドも `None` は「指定なし」を意味する。
#[derive(Debug, Clone, Default)]
pub struct VoyageSearchCriteria {
    /// 出発港（UN/LOCODE）。
    pub origin: Option<Location>,
    /// 到着港（UN/LOCODE）。
    pub destination: Option<Location>,
    /// 対応貨物種別。
    pub cargo_type: Option<CargoType>,
    /// 出発日（先頭区間の出発日）の下限（この日以降・両端含む・US07）。
    pub departure_from: Option<chrono::NaiveDate>,
    /// 出発日（先頭区間の出発日）の上限（この日以前・両端含む・US07）。
    pub departure_to: Option<chrono::NaiveDate>,
}

/// 航海リポジトリの出力ポート。
#[async_trait::async_trait]
pub trait VoyageRepository: Send + Sync {
    /// 航海を永続化する（新規登録・更新の双方に対応する upsert）。
    async fn save(&self, voyage: &Voyage) -> Result<(), RepositoryError>;

    /// 航海番号で検索する。
    async fn find_by_voyage_number(
        &self,
        number: &VoyageNumber,
    ) -> Result<Option<Voyage>, RepositoryError>;

    /// 指定航海番号が既に存在するかを返す。
    async fn exists(&self, number: &VoyageNumber) -> Result<bool, RepositoryError>;

    /// 検索条件を満たす航海の一覧を返す（US07）。
    async fn search(&self, criteria: &VoyageSearchCriteria)
    -> Result<Vec<Voyage>, RepositoryError>;

    /// 全航海を返す（航路一覧の初期表示用）。
    async fn find_all(&self) -> Result<Vec<Voyage>, RepositoryError>;
}

/// `Arc<dyn VoyageRepository>` へ委譲するブランケット実装。
///
/// composition root で注入したトレイトオブジェクトを、ジェネリックな
/// アプリケーションサービス（`VoyageCommandService<R>` 等）へ渡せるようにする（ADR-0003）。
#[async_trait::async_trait]
impl VoyageRepository for std::sync::Arc<dyn VoyageRepository> {
    async fn save(&self, voyage: &Voyage) -> Result<(), RepositoryError> {
        (**self).save(voyage).await
    }
    async fn find_by_voyage_number(
        &self,
        number: &VoyageNumber,
    ) -> Result<Option<Voyage>, RepositoryError> {
        (**self).find_by_voyage_number(number).await
    }
    async fn exists(&self, number: &VoyageNumber) -> Result<bool, RepositoryError> {
        (**self).exists(number).await
    }
    async fn search(
        &self,
        criteria: &VoyageSearchCriteria,
    ) -> Result<Vec<Voyage>, RepositoryError> {
        (**self).search(criteria).await
    }
    async fn find_all(&self) -> Result<Vec<Voyage>, RepositoryError> {
        (**self).find_all().await
    }
}

/// ACL ポートのエラー。外部コンテキスト（Booking）参照時の抽象エラー。
#[derive(Debug, thiserror::Error)]
pub enum AclError {
    /// 外部コンテキスト参照時のエラー。
    #[error("acl failure: {0}")]
    Backend(String),
}

/// 予約に紐づく貨物仕様（経路探索の入力）。
///
/// Booking Context の貨物（Cargo）から必要な項目のみを Routing 向けに射影した ACL の DTO。
/// domain-booking への依存を避けるため、Routing 固有の型（`Location`・`CargoType`）で表現する。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CargoSpec {
    /// 出発地（UN/LOCODE）。
    pub origin: Location,
    /// 目的地（UN/LOCODE）。
    pub destination: Location,
    /// 到着期限。
    pub arrival_deadline: NaiveDate,
    /// 貨物種別。
    pub cargo_type: CargoType,
}

/// 予約番号から貨物仕様を取得する ACL ポート（US07/US08）。
///
/// Routing Context は Booking Context を直接参照せず、この trait を通じてのみ貨物仕様を得る。
#[async_trait::async_trait]
pub trait CargoSpecProvider: Send + Sync {
    /// 予約番号（booking_id）に紐づく貨物仕様を取得する。存在しなければ `None`。
    async fn find_cargo_spec(&self, booking_id: &str) -> Result<Option<CargoSpec>, AclError>;
}

/// `Arc<dyn CargoSpecProvider>` へ委譲するブランケット実装（ADR-0003）。
#[async_trait::async_trait]
impl CargoSpecProvider for std::sync::Arc<dyn CargoSpecProvider> {
    async fn find_cargo_spec(&self, booking_id: &str) -> Result<Option<CargoSpec>, AclError> {
        (**self).find_cargo_spec(booking_id).await
    }
}
