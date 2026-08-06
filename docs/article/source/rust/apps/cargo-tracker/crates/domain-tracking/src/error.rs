//! Tracking Context のドメインエラー。

use thiserror::Error;

/// 追跡ドメインのエラー。
#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum TrackingError {
    /// 追跡番号が空。
    #[error("追跡番号が空です")]
    EmptyTrackingNumber,
    /// 予約 ID が空。
    #[error("予約 ID が空です")]
    EmptyBookingId,
    /// 追跡位置（UN/LOCODE）が不正。
    #[error("追跡位置が不正です: {0}")]
    InvalidLocation(String),
}
