//! 荷主の読み取り側（CQRS クエリ）。
//!
//! Read Model DTO とクエリポートを app 層に定義し、sqlx 実装は infra-persistence に置く（ADR-0001）。

/// 荷主検索結果の Read Model DTO（画面表示・API 用）。
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize)]
pub struct ShipperSummary {
    /// 荷主 ID（UUID 文字列）。
    pub id: String,
    /// 荷主コード。
    pub code: String,
    /// 荷主名。
    pub name: String,
    /// メールアドレス。
    pub email: String,
}

/// クエリ実行のエラー。
#[derive(Debug, thiserror::Error)]
pub enum QueryError {
    /// 読み取り層のエラー。
    #[error("query error: {0}")]
    Backend(String),
}

/// 荷主クエリポート。実装は infra-persistence（`SqlxShipperQueryAdapter`）が担う。
#[async_trait::async_trait]
pub trait ShipperQueryPort: Send + Sync {
    /// 名称またはメールの部分一致で荷主を検索する（`limit` 件まで）。
    async fn search(&self, keyword: &str, limit: i64) -> Result<Vec<ShipperSummary>, QueryError>;
}

/// 荷主検索クエリサービス。
pub struct FindShipperQueryService<Q: ShipperQueryPort> {
    port: Q,
}

impl<Q: ShipperQueryPort> FindShipperQueryService<Q> {
    /// サービスを生成する。
    pub fn new(port: Q) -> Self {
        Self { port }
    }

    /// キーワードで荷主を検索する。空キーワードは空結果を返す。
    ///
    /// # Errors
    ///
    /// 読み取り層のエラー時は `QueryError` を返す。
    pub async fn search(&self, keyword: &str) -> Result<Vec<ShipperSummary>, QueryError> {
        if keyword.trim().is_empty() {
            return Ok(Vec::new());
        }
        self.port.search(keyword.trim(), 10).await
    }
}
