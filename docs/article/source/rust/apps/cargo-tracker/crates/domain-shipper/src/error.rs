//! Shipper Context のドメインエラー。

/// 荷主ドメインのエラー型。
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum ShipperError {
    /// 荷主名が空、または長すぎる場合。
    #[error("shipper name must be 1..=200 chars")]
    InvalidShipperName,
    /// メールアドレスの形式が不正な場合。
    #[error("invalid email: {0}")]
    InvalidEmail(String),
    /// 電話番号の形式が不正な場合。
    #[error("invalid phone: {0}")]
    InvalidPhone(String),
    /// 住所が長すぎる場合。
    #[error("address must be <= 500 chars")]
    InvalidAddress,
    /// 契約番号が空の場合。
    #[error("contract number must not be empty")]
    InvalidContractNumber,
    /// 割引率が範囲外（0.0000〜0.3000）の場合。
    #[error("discount rate must be within 0.0000..=0.3000")]
    InvalidDiscountRate,
}
