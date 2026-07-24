//! Billing Context の集約ルート `FreightCharge`（輸送料金）。

use crate::error::BillingError;
use crate::value_objects::{
    AdjustmentReason, BillingBookingId, ChargeStatus, DiscountRate, FreightChargeId, Money,
};
use serde::{Deserialize, Serialize};

/// 料金調整（例外時の減額・補償費用）。金額は正の `Money` で保持し、常に料金からの控除として扱う。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ChargeAdjustment {
    reason: AdjustmentReason,
    amount: Money,
}

impl ChargeAdjustment {
    /// 調整を生成する。
    #[must_use]
    pub fn new(reason: AdjustmentReason, amount: Money) -> Self {
        Self { reason, amount }
    }

    /// 理由。
    #[must_use]
    pub fn reason(&self) -> AdjustmentReason {
        self.reason
    }

    /// 控除金額。
    #[must_use]
    pub fn amount(&self) -> Money {
        self.amount
    }
}

/// 割引明細（US22・割引根拠を保持: 割引率・基本料金・割引額）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DiscountLine {
    rate: DiscountRate,
    base: Money,
    discount_amount: Money,
}

impl DiscountLine {
    /// 永続化済みデータから割引明細を再構築する。
    #[must_use]
    pub fn reconstruct(rate: DiscountRate, base: Money, discount_amount: Money) -> Self {
        Self {
            rate,
            base,
            discount_amount,
        }
    }

    /// 割引率。
    #[must_use]
    pub fn rate(&self) -> DiscountRate {
        self.rate
    }

    /// 適用対象の基本料金。
    #[must_use]
    pub fn base(&self) -> Money {
        self.base
    }

    /// 割引額。
    #[must_use]
    pub fn discount_amount(&self) -> Money {
        self.discount_amount
    }
}

/// 輸送料金の集約ルート（US21/US22）。
///
/// 基本料金に例外調整（減額・補償）と法人割引を適用し、確定（`Confirmed`）すると精算（US23）へ
/// 引き渡せる。BC 独立のため他コンテキストの domain クレートに依存しない。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FreightCharge {
    charge_id: FreightChargeId,
    booking_id: BillingBookingId,
    base_amount: Money,
    adjustments: Vec<ChargeAdjustment>,
    discount: Option<DiscountLine>,
    status: ChargeStatus,
}

impl FreightCharge {
    /// 基本料金から下書きの輸送料金を生成する（US21・引取済予約に対して）。
    #[must_use]
    pub fn create(
        charge_id: FreightChargeId,
        booking_id: BillingBookingId,
        base_amount: Money,
    ) -> Self {
        Self {
            charge_id,
            booking_id,
            base_amount,
            adjustments: Vec::new(),
            discount: None,
            status: ChargeStatus::Draft,
        }
    }

    /// 永続化済みデータから再構築する。
    #[must_use]
    pub fn reconstruct(
        charge_id: FreightChargeId,
        booking_id: BillingBookingId,
        base_amount: Money,
        adjustments: Vec<ChargeAdjustment>,
        discount: Option<DiscountLine>,
        status: ChargeStatus,
    ) -> Self {
        Self {
            charge_id,
            booking_id,
            base_amount,
            adjustments,
            discount,
            status,
        }
    }

    /// 料金 ID。
    #[must_use]
    pub fn charge_id(&self) -> &FreightChargeId {
        &self.charge_id
    }

    /// 予約 ID。
    #[must_use]
    pub fn booking_id(&self) -> &BillingBookingId {
        &self.booking_id
    }

    /// 基本料金。
    #[must_use]
    pub fn base_amount(&self) -> Money {
        self.base_amount
    }

    /// 調整一覧。
    #[must_use]
    pub fn adjustments(&self) -> &[ChargeAdjustment] {
        &self.adjustments
    }

    /// 割引明細。
    #[must_use]
    pub fn discount(&self) -> Option<&DiscountLine> {
        self.discount.as_ref()
    }

    /// 状態。
    #[must_use]
    pub fn status(&self) -> ChargeStatus {
        self.status
    }

    /// 例外調整（減額・補償費用）を追加する（US21）。
    ///
    /// # Errors
    ///
    /// 確定済みなら [`BillingError::AlreadyConfirmed`]。
    pub fn add_adjustment(&mut self, adjustment: ChargeAdjustment) -> Result<(), BillingError> {
        self.ensure_draft()?;
        self.adjustments.push(adjustment);
        Ok(())
    }

    /// 法人割引を適用する（US22）。基本料金に割引率を乗じた額を割引として記録する。
    /// 割引率 0%（個人荷主等）の場合は割引を記録しない。
    ///
    /// # Errors
    ///
    /// 確定済みなら [`BillingError::AlreadyConfirmed`]。
    pub fn apply_discount(&mut self, rate: DiscountRate) -> Result<(), BillingError> {
        self.ensure_draft()?;
        if !rate.is_applicable() {
            self.discount = None;
            return Ok(());
        }
        let discount_amount = self.base_amount.multiply_ratio(rate.value());
        self.discount = Some(DiscountLine {
            rate,
            base: self.base_amount,
            discount_amount,
        });
        Ok(())
    }

    /// 合計金額を算出する（基本料金 − 調整控除 − 割引額）。
    ///
    /// # Errors
    ///
    /// 通貨不一致は [`BillingError::CurrencyMismatch`]。
    pub fn total(&self) -> Result<Money, BillingError> {
        let mut total = self.base_amount;
        for adjustment in &self.adjustments {
            total = total.subtract(&adjustment.amount())?;
        }
        if let Some(discount) = &self.discount {
            total = total.subtract(&discount.discount_amount)?;
        }
        Ok(total)
    }

    /// 料金を確定する（US21・Draft → Confirmed）。
    ///
    /// # Errors
    ///
    /// 既に確定済みなら [`BillingError::AlreadyConfirmed`]。
    pub fn confirm(&mut self) -> Result<(), BillingError> {
        self.ensure_draft()?;
        self.status = ChargeStatus::Confirmed;
        Ok(())
    }

    fn ensure_draft(&self) -> Result<(), BillingError> {
        match self.status {
            ChargeStatus::Draft => Ok(()),
            ChargeStatus::Confirmed => Err(BillingError::AlreadyConfirmed),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::value_objects::Currency;
    use rust_decimal::Decimal;
    use rust_decimal::prelude::*;

    fn charge(base: i64) -> FreightCharge {
        FreightCharge::create(
            FreightChargeId::generate(),
            BillingBookingId::parse("BKG-1").unwrap(),
            Money::jpy(Decimal::from(base)),
        )
    }

    #[test]
    fn 生成直後は下書きで合計は基本料金に一致する() {
        let c = charge(100_000);
        assert_eq!(c.status(), ChargeStatus::Draft);
        assert_eq!(c.total().unwrap(), Money::jpy(Decimal::from(100_000)));
    }

    #[test]
    fn 例外調整は合計から控除される() {
        let mut c = charge(100_000);
        c.add_adjustment(ChargeAdjustment::new(
            AdjustmentReason::DelayReduction,
            Money::jpy(Decimal::from(5_000)),
        ))
        .unwrap();
        c.add_adjustment(ChargeAdjustment::new(
            AdjustmentReason::DamageCompensation,
            Money::jpy(Decimal::from(3_000)),
        ))
        .unwrap();
        assert_eq!(c.total().unwrap(), Money::jpy(Decimal::from(92_000)));
    }

    #[test]
    fn 法人割引は基本料金に適用され割引後金額になる() {
        let mut c = charge(100_000);
        let rate = DiscountRate::new(Decimal::from_str("0.10").unwrap()).unwrap();
        c.apply_discount(rate).unwrap();
        let line = c.discount().unwrap();
        assert_eq!(line.discount_amount(), Money::jpy(Decimal::from(10_000)));
        assert_eq!(line.base(), Money::jpy(Decimal::from(100_000)));
        assert_eq!(c.total().unwrap(), Money::jpy(Decimal::from(90_000)));
    }

    #[test]
    fn 割引率ゼロの個人荷主は割引が記録されず合計は基本料金のまま() {
        let mut c = charge(100_000);
        c.apply_discount(DiscountRate::zero()).unwrap();
        assert!(c.discount().is_none());
        assert_eq!(c.total().unwrap(), Money::jpy(Decimal::from(100_000)));
    }

    #[test]
    fn 調整と割引を併用すると調整控除後に割引を差し引く() {
        // 基本 100,000 − 減額 5,000 − 割引(基本×10%=10,000) = 85,000
        let mut c = charge(100_000);
        c.add_adjustment(ChargeAdjustment::new(
            AdjustmentReason::DelayReduction,
            Money::jpy(Decimal::from(5_000)),
        ))
        .unwrap();
        c.apply_discount(DiscountRate::new(Decimal::from_str("0.10").unwrap()).unwrap())
            .unwrap();
        assert_eq!(c.total().unwrap(), Money::jpy(Decimal::from(85_000)));
    }

    #[test]
    fn 確定すると状態がconfirmedになり再操作は拒否される() {
        let mut c = charge(100_000);
        c.confirm().unwrap();
        assert_eq!(c.status(), ChargeStatus::Confirmed);
        assert_eq!(c.confirm(), Err(BillingError::AlreadyConfirmed));
        assert_eq!(
            c.apply_discount(DiscountRate::zero()),
            Err(BillingError::AlreadyConfirmed)
        );
        assert_eq!(
            c.add_adjustment(ChargeAdjustment::new(
                AdjustmentReason::DelayReduction,
                Money::zero(Currency::Jpy)
            )),
            Err(BillingError::AlreadyConfirmed)
        );
    }
}
