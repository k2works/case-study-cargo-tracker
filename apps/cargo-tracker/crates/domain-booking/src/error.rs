//! Booking Context のドメインエラー。

/// 貨物予約ドメインのエラー型。
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum BookingError {
    /// 予約 ID が空の場合。
    #[error("booking id must not be empty")]
    InvalidBookingId,
    /// 出発地と目的地が同一の場合。
    #[error("origin and destination must differ")]
    SameOriginAndDestination,
    /// 重量が 0 以下の場合。
    #[error("weight must be greater than zero")]
    InvalidWeight,
    /// 個数が 0 以下の場合。
    #[error("quantity must be greater than zero")]
    InvalidQuantity,
    /// 品名が長すぎる場合。
    #[error("description must be <= 500 chars")]
    InvalidDescription,
    /// 荷受人情報が不正な場合。
    #[error("invalid consignee: {0}")]
    InvalidConsignee(String),
    /// 危険物なのに危険物申告が欠落している場合。
    #[error("hazardous cargo requires a hazardous declaration")]
    MissingHazardousDeclaration,
    /// 冷凍・冷蔵貨物なのに温度管理条件が欠落している場合。
    #[error("refrigerated cargo requires a temperature requirement")]
    MissingTemperatureRequirement,
    /// 危険物申告の内容が不正な場合。
    #[error("invalid hazardous declaration")]
    InvalidHazardousDeclaration,
    /// 温度管理条件が不正な場合（最低 > 最高）。
    #[error("invalid temperature requirement")]
    InvalidTemperatureRequirement,
}
