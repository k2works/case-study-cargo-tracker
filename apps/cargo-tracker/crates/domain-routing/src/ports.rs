//! Routing Context の出力ポート（trait）。実装はインフラ層で行う。

use crate::aggregate::Voyage;
use crate::value_objects::{CargoType, VoyageNumber};
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
