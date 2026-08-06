//! Estimation Context のドメインエラー。

use thiserror::Error;

/// 見積ドメインのエラー。
#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum EstimationError {
    /// 出発地と目的地が同一。
    #[error("出発地と目的地が同一です")]
    SameOriginAndDestination,
    /// 重量が 0 以下。
    #[error("重量は 0 より大きい必要があります")]
    InvalidWeight,
    /// 位置（UN/LOCODE）が不正。
    #[error("位置が不正です: {0}")]
    InvalidLocation(String),
}
