//! Billing Context のドメインエラー。

use thiserror::Error;

/// 請求ドメインのエラー。
#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum BillingError {
    /// 予約 ID が空。
    #[error("予約 ID が空です")]
    EmptyBookingId,
    /// 金額の通貨が一致しない（演算不可）。
    #[error("通貨が一致しません: {left} と {right}")]
    CurrencyMismatch {
        /// 左辺の通貨コード。
        left: String,
        /// 右辺の通貨コード。
        right: String,
    },
    /// 割引率が範囲外（0.0000〜0.3000）。
    #[error("割引率は 0.0000〜0.3000 の範囲である必要があります")]
    InvalidDiscountRate,
    /// 確定済みの料金は再操作できない。
    #[error("確定済みの料金は変更できません")]
    AlreadyConfirmed,
    /// 精算書番号が空。
    #[error("精算書番号が空です")]
    EmptyInvoiceNumber,
    /// 既に入金確認済みの精算書への再入金確認。
    #[error("既に入金確認済みの精算書です")]
    AlreadyPaid,
}
