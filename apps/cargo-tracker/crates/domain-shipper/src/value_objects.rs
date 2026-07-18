//! Shipper Context の値オブジェクト。

use crate::error::ShipperError;
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// 荷主コード（`SHP-` プレフィックス + UUID 先頭 8 文字を自動生成）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ShipperCode(String);

impl ShipperCode {
    /// 新しい荷主コードを自動生成する。
    #[must_use]
    pub fn generate() -> Self {
        let uuid = Uuid::new_v4().simple().to_string();
        Self(format!("SHP-{}", &uuid[..8].to_uppercase()))
    }

    /// 既存の文字列から荷主コードを構成する（永続化からの復元用）。
    #[must_use]
    pub fn from_string(value: String) -> Self {
        Self(value)
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 荷主名（氏名または社名。1〜200 文字）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ShipperName(String);

impl ShipperName {
    /// 荷主名を生成する。
    ///
    /// # Errors
    ///
    /// 空、または 200 文字を超える場合は `ShipperError::InvalidShipperName` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, ShipperError> {
        let value = value.into();
        let trimmed = value.trim();
        if trimmed.is_empty() || trimmed.chars().count() > 200 {
            return Err(ShipperError::InvalidShipperName);
        }
        Ok(Self(trimmed.to_string()))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// メールアドレス。システム全体で一意（一意性はリポジトリで担保）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Email(String);

impl Email {
    /// メールアドレスを生成する。
    ///
    /// # Errors
    ///
    /// `@` を含まない等の不正な形式の場合は `ShipperError::InvalidEmail` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, ShipperError> {
        let value = value.into();
        let trimmed = value.trim();
        // 最小限の形式検証（local@domain、ドメインに `.` を含む）。
        let valid = match trimmed.split_once('@') {
            Some((local, domain)) => {
                !local.is_empty() && domain.contains('.') && !domain.starts_with('.')
            }
            None => false,
        };
        if !valid || trimmed.chars().count() > 200 {
            return Err(ShipperError::InvalidEmail(trimmed.to_string()));
        }
        Ok(Self(trimmed.to_string()))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 電話番号（任意項目）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Phone(String);

impl Phone {
    /// 電話番号を生成する。
    ///
    /// # Errors
    ///
    /// 空、または 50 文字を超える場合は `ShipperError::InvalidPhone` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, ShipperError> {
        let value = value.into();
        let trimmed = value.trim();
        if trimmed.is_empty() || trimmed.chars().count() > 50 {
            return Err(ShipperError::InvalidPhone(trimmed.to_string()));
        }
        Ok(Self(trimmed.to_string()))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 住所（任意項目。最大 500 文字）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Address(String);

impl Address {
    /// 住所を生成する。
    ///
    /// # Errors
    ///
    /// 500 文字を超える場合は `ShipperError::InvalidAddress` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, ShipperError> {
        let value = value.into();
        let trimmed = value.trim();
        if trimmed.chars().count() > 500 {
            return Err(ShipperError::InvalidAddress);
        }
        Ok(Self(trimmed.to_string()))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 契約番号（法人荷主のみ）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ContractNumber(String);

impl ContractNumber {
    /// 契約番号を生成する。
    ///
    /// # Errors
    ///
    /// 空の場合は `ShipperError::InvalidContractNumber` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, ShipperError> {
        let value = value.into();
        let trimmed = value.trim();
        if trimmed.is_empty() {
            return Err(ShipperError::InvalidContractNumber);
        }
        Ok(Self(trimmed.to_string()))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 割引率（0.0000〜0.3000、最大 30%）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct DiscountRate(Decimal);

impl DiscountRate {
    /// 割引率を生成する。
    ///
    /// # Errors
    ///
    /// 0.0000〜0.3000 の範囲外の場合は `ShipperError::InvalidDiscountRate` を返す。
    pub fn new(value: Decimal) -> Result<Self, ShipperError> {
        let max = Decimal::new(3000, 4); // 0.3000
        if value < Decimal::ZERO || value > max {
            return Err(ShipperError::InvalidDiscountRate);
        }
        Ok(Self(value))
    }

    /// 内部の `Decimal` 値を返す。
    #[must_use]
    pub fn value(&self) -> Decimal {
        self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 荷主コードは_shp_プレフィックスで生成される() {
        let code = ShipperCode::generate();
        assert!(code.as_str().starts_with("SHP-"));
        assert_eq!(code.as_str().len(), 12); // "SHP-" + 8 文字
    }

    #[test]
    fn 荷主名は空だとエラーになる() {
        assert_eq!(
            ShipperName::new("  "),
            Err(ShipperError::InvalidShipperName)
        );
        assert!(ShipperName::new("山田商事").is_ok());
    }

    #[test]
    fn メールは形式検証される() {
        assert!(Email::new("user@example.com").is_ok());
        assert!(Email::new("invalid").is_err());
        assert!(Email::new("user@localhost").is_err());
    }

    #[test]
    fn 割引率は0から30パーセントの範囲() {
        assert!(DiscountRate::new(Decimal::new(3000, 4)).is_ok()); // 30%
        assert!(DiscountRate::new(Decimal::ZERO).is_ok());
        assert_eq!(
            DiscountRate::new(Decimal::new(3001, 4)),
            Err(ShipperError::InvalidDiscountRate)
        );
        assert_eq!(
            DiscountRate::new(Decimal::new(-1, 4)),
            Err(ShipperError::InvalidDiscountRate)
        );
    }

    #[test]
    fn 契約番号は空だとエラーになる() {
        assert!(ContractNumber::new("C-12345").is_ok());
        assert!(ContractNumber::new("").is_err());
    }
}
