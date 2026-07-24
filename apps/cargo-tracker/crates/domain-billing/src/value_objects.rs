//! Billing Context の値オブジェクトと列挙型。
//!
//! `Money`・`DiscountRate` は BC 独立のため Billing ローカルに定義する（shared-kernel へ昇格しない・
//! ADR-0010）。他コンテキストの同名型（Shipper の `DiscountRate` 等）とは別型として扱う。

use crate::error::BillingError;
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};

/// 通貨コード（ISO 4217）。本システムは JPY のみ扱う。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum Currency {
    /// 日本円。
    Jpy,
}

impl Currency {
    /// ISO 4217 の文字列表現。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Jpy => "JPY",
        }
    }

    /// 文字列から復元する。本システムは JPY のみ扱うため常に `Jpy`。
    #[must_use]
    pub fn from_str_or_jpy(_value: &str) -> Self {
        Self::Jpy
    }
}

/// 金額（値＋通貨）。浮動小数点を使わず `Decimal` で保持する。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Money {
    amount: Decimal,
    currency: Currency,
}

impl Money {
    /// 円建ての金額を生成する。
    #[must_use]
    pub fn jpy(amount: Decimal) -> Self {
        Self {
            amount,
            currency: Currency::Jpy,
        }
    }

    /// 指定通貨のゼロ金額。
    #[must_use]
    pub fn zero(currency: Currency) -> Self {
        Self {
            amount: Decimal::ZERO,
            currency,
        }
    }

    /// 金額の生値。
    #[must_use]
    pub fn amount(&self) -> Decimal {
        self.amount
    }

    /// 通貨。
    #[must_use]
    pub fn currency(&self) -> Currency {
        self.currency
    }

    /// 加算する。通貨不一致は `CurrencyMismatch`。
    ///
    /// # Errors
    ///
    /// 通貨が一致しない場合は [`BillingError::CurrencyMismatch`]。
    pub fn add(&self, other: &Self) -> Result<Self, BillingError> {
        self.ensure_same_currency(other)?;
        Ok(Self {
            amount: self.amount + other.amount,
            currency: self.currency,
        })
    }

    /// 減算する。通貨不一致は `CurrencyMismatch`。
    ///
    /// # Errors
    ///
    /// 通貨が一致しない場合は [`BillingError::CurrencyMismatch`]。
    pub fn subtract(&self, other: &Self) -> Result<Self, BillingError> {
        self.ensure_same_currency(other)?;
        Ok(Self {
            amount: self.amount - other.amount,
            currency: self.currency,
        })
    }

    /// 割合（`Decimal`）を乗じた金額を返す（割引額算出等）。結果は円未満を丸める。
    #[must_use]
    pub fn multiply_ratio(&self, ratio: Decimal) -> Self {
        Self {
            amount: self.amount * ratio,
            currency: self.currency,
        }
        .rounded()
    }

    /// 円未満を丸めた金額を返す（JPY は最小単位が 1 円・ADR-0010）。
    ///
    /// 四捨五入（`MidpointAwayFromZero`・請求で一般的な丸め）で小数第 0 位に丸める。
    #[must_use]
    pub fn rounded(&self) -> Self {
        Self {
            amount: self
                .amount
                .round_dp_with_strategy(0, rust_decimal::RoundingStrategy::MidpointAwayFromZero),
            currency: self.currency,
        }
    }

    fn ensure_same_currency(&self, other: &Self) -> Result<(), BillingError> {
        if self.currency != other.currency {
            return Err(BillingError::CurrencyMismatch {
                left: self.currency.as_str().to_string(),
                right: other.currency.as_str().to_string(),
            });
        }
        Ok(())
    }
}

/// 割引率（法人契約割引・0.0000〜0.3000）。Billing ローカル型（BC 独立）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct DiscountRate(Decimal);

impl DiscountRate {
    /// 上限（30%）。
    const MAX: Decimal = Decimal::from_parts(3000, 0, 0, false, 4); // 0.3000

    /// 割引率を生成する。0.0000〜0.3000 の範囲外は `InvalidDiscountRate`。
    ///
    /// # Errors
    ///
    /// 範囲外の場合は [`BillingError::InvalidDiscountRate`]。
    pub fn new(value: Decimal) -> Result<Self, BillingError> {
        if value < Decimal::ZERO || value > Self::MAX {
            return Err(BillingError::InvalidDiscountRate);
        }
        Ok(Self(value))
    }

    /// 割引なし（0%）。
    #[must_use]
    pub fn zero() -> Self {
        Self(Decimal::ZERO)
    }

    /// 割引率の生値（例: 0.10）。
    #[must_use]
    pub fn value(&self) -> Decimal {
        self.0
    }

    /// 割引が実質的に発生するか（0% 超）。
    #[must_use]
    pub fn is_applicable(&self) -> bool {
        self.0 > Decimal::ZERO
    }
}

/// 輸送料金 ID（UUID ベース・`FRC-` プレフィックス）。
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct FreightChargeId(String);

impl FreightChargeId {
    /// 料金 ID を生成する。
    #[must_use]
    pub fn generate() -> Self {
        Self(format!("FRC-{}", uuid::Uuid::new_v4().simple()))
    }

    /// 文字列から復元する。
    #[must_use]
    pub fn from_string(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    /// 文字列表現。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// Billing から参照する予約 ID（BC 固有型）。
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct BillingBookingId(String);

impl BillingBookingId {
    /// 予約 ID を生成する。空文字は `EmptyBookingId`。
    ///
    /// # Errors
    ///
    /// 空の場合は [`BillingError::EmptyBookingId`]。
    pub fn parse(value: impl Into<String>) -> Result<Self, BillingError> {
        let value = value.into();
        if value.trim().is_empty() {
            return Err(BillingError::EmptyBookingId);
        }
        Ok(Self(value))
    }

    /// 文字列表現。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 輸送料金の状態。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ChargeStatus {
    /// 下書き（算出中・調整可能）。
    Draft,
    /// 確定（精算へ引き渡せる）。
    Confirmed,
}

impl ChargeStatus {
    /// 永続化用の文字列表現。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Draft => "DRAFT",
            Self::Confirmed => "CONFIRMED",
        }
    }

    /// 文字列から復元する。未知の値は `Draft`。
    #[must_use]
    pub fn from_str_or_draft(value: &str) -> Self {
        match value {
            "CONFIRMED" => Self::Confirmed,
            _ => Self::Draft,
        }
    }
}

/// 料金調整の理由（US21・例外時の減額/補償）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AdjustmentReason {
    /// 遅延による減額。
    DelayReduction,
    /// 破損に対する補償費用。
    DamageCompensation,
}

impl AdjustmentReason {
    /// 永続化用の文字列表現。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::DelayReduction => "DELAY_REDUCTION",
            Self::DamageCompensation => "DAMAGE_COMPENSATION",
        }
    }

    /// 文字列から復元する。未知の値は `None`。
    #[must_use]
    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "DELAY_REDUCTION" => Some(Self::DelayReduction),
            "DAMAGE_COMPENSATION" => Some(Self::DamageCompensation),
            _ => None,
        }
    }

    /// 画面表示用ラベル。
    #[must_use]
    pub fn label(&self) -> &'static str {
        match self {
            Self::DelayReduction => "遅延減額",
            Self::DamageCompensation => "破損補償費用",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rust_decimal::prelude::*;

    #[test]
    fn 同一通貨の金額は加減算できる() {
        let a = Money::jpy(Decimal::from(100_000));
        let b = Money::jpy(Decimal::from(30_000));
        assert_eq!(a.add(&b).unwrap().amount(), Decimal::from(130_000));
        assert_eq!(a.subtract(&b).unwrap().amount(), Decimal::from(70_000));
    }

    #[test]
    fn 金額に割合を乗じられる() {
        let base = Money::jpy(Decimal::from(100_000));
        let discount = base.multiply_ratio(Decimal::from_str("0.10").unwrap());
        assert_eq!(discount.amount(), Decimal::from(10_000));
    }

    #[test]
    fn 割合乗算は円未満を四捨五入する() {
        // 100,001 × 0.15 = 15,000.15 → 15,000（円未満切り捨て側）。
        let base = Money::jpy(Decimal::from(100_001));
        let discount = base.multiply_ratio(Decimal::from_str("0.15").unwrap());
        assert_eq!(discount.amount(), Decimal::from(15_000));
        // 100,003 × 0.15 = 15,000.45 → 15,000。
        let d2 =
            Money::jpy(Decimal::from(100_003)).multiply_ratio(Decimal::from_str("0.15").unwrap());
        assert_eq!(d2.amount(), Decimal::from(15_000));
        // rounded() 単体: 15,000.5 → 15,001（四捨五入・MidpointAwayFromZero）。
        assert_eq!(
            Money::jpy(Decimal::from_str("15000.5").unwrap())
                .rounded()
                .amount(),
            Decimal::from(15_001)
        );
    }

    #[test]
    fn 割引率は0から30パーセントの範囲のみ許容する() {
        assert!(DiscountRate::new(Decimal::from_str("0.30").unwrap()).is_ok());
        assert!(DiscountRate::new(Decimal::ZERO).is_ok());
        assert_eq!(
            DiscountRate::new(Decimal::from_str("0.3001").unwrap()),
            Err(BillingError::InvalidDiscountRate)
        );
        assert_eq!(
            DiscountRate::new(Decimal::from_str("-0.01").unwrap()),
            Err(BillingError::InvalidDiscountRate)
        );
    }

    #[test]
    fn 割引率ゼロは適用対象外() {
        assert!(!DiscountRate::zero().is_applicable());
        assert!(
            DiscountRate::new(Decimal::from_str("0.10").unwrap())
                .unwrap()
                .is_applicable()
        );
    }

    #[test]
    fn 予約idは空だとエラー() {
        assert_eq!(
            BillingBookingId::parse("  "),
            Err(BillingError::EmptyBookingId)
        );
        assert!(BillingBookingId::parse("BKG-1").is_ok());
    }

    #[test]
    fn 料金状態と調整理由は永続化文字列と往復できる() {
        assert_eq!(
            ChargeStatus::from_str_or_draft("CONFIRMED"),
            ChargeStatus::Confirmed
        );
        assert_eq!(ChargeStatus::Draft.as_str(), "DRAFT");
        assert_eq!(
            AdjustmentReason::parse("DAMAGE_COMPENSATION"),
            Some(AdjustmentReason::DamageCompensation)
        );
        assert_eq!(AdjustmentReason::parse("X"), None);
    }
}
