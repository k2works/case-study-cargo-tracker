//! Handling Context のドメインエラー。

use thiserror::Error;

/// 荷役ドメインのエラー。
#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum HandlingError {
    /// 予約 ID が空。
    #[error("予約 ID が空です")]
    EmptyBookingId,
    /// 作業場所（UN/LOCODE）が不正。
    #[error("作業場所が不正です: {0}")]
    InvalidLocation(String),
    /// 引取（Claim）に荷受人確認が無い（US16 の不変条件）。
    #[error("引取作業には荷受人確認（署名または確認コード）が必要です")]
    ReceiptConfirmationRequired,
    /// 荷受人確認が空。
    #[error("荷受人確認が空です")]
    EmptyReceiptConfirmation,
}
