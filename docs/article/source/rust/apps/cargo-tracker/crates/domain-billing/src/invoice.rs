//! Billing Context の集約ルート `Invoice`（精算書）。
//!
//! 確定した輸送料金（`FreightCharge`・ADR-0009）を入力に精算書を発行し、消費税を適用して
//! 請求金額を確定する。入金確認で `Confirmed`、支払期限超過で `Overdue` へ遷移する（US23）。

use crate::error::BillingError;
use crate::value_objects::{BillingBookingId, InvoiceId, Money, PaymentMethod, PaymentStatus};
use chrono::{DateTime, NaiveDate, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};

/// 既定の消費税率（10%・US23）。名前付き定数で金額リグレッションを固定する。
pub const DEFAULT_TAX_RATE: Decimal = Decimal::from_parts(1000, 0, 0, false, 4); // 0.1000

/// 精算明細（US23・請求内訳）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct InvoiceLineItem {
    description: String,
    amount: Money,
    seq_number: i32,
}

impl InvoiceLineItem {
    /// 明細を生成する。
    #[must_use]
    pub fn new(description: impl Into<String>, amount: Money, seq_number: i32) -> Self {
        Self {
            description: description.into(),
            amount,
            seq_number,
        }
    }

    /// 内容。
    #[must_use]
    pub fn description(&self) -> &str {
        &self.description
    }

    /// 金額。
    #[must_use]
    pub fn amount(&self) -> Money {
        self.amount
    }

    /// 表示順。
    #[must_use]
    pub fn seq_number(&self) -> i32 {
        self.seq_number
    }
}

/// 支払記録（US23・入金確認時に記録）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Payment {
    paid_amount: Money,
    paid_at: DateTime<Utc>,
    payment_method: PaymentMethod,
    transaction_reference: Option<String>,
}

impl Payment {
    /// 支払記録を生成する。
    #[must_use]
    pub fn new(
        paid_amount: Money,
        paid_at: DateTime<Utc>,
        payment_method: PaymentMethod,
        transaction_reference: Option<String>,
    ) -> Self {
        Self {
            paid_amount,
            paid_at,
            payment_method,
            transaction_reference,
        }
    }

    /// 支払金額。
    #[must_use]
    pub fn paid_amount(&self) -> Money {
        self.paid_amount
    }

    /// 支払日時。
    #[must_use]
    pub fn paid_at(&self) -> DateTime<Utc> {
        self.paid_at
    }

    /// 支払方法。
    #[must_use]
    pub fn payment_method(&self) -> PaymentMethod {
        self.payment_method
    }

    /// 取引参照番号。
    #[must_use]
    pub fn transaction_reference(&self) -> Option<&str> {
        self.transaction_reference.as_deref()
    }
}

/// 消費税額を算定する純粋関数（円未満四捨五入・ADR-0010）。
#[must_use]
pub fn calculate_tax(charge_total: Money, tax_rate: Decimal) -> Money {
    charge_total.multiply_ratio(tax_rate)
}

/// 精算書の集約ルート（US23）。
///
/// 確定料金（割引後）に消費税を適用して請求金額を確定し、入金確認・期限超過を管理する。
/// BC 独立のため他コンテキストの domain クレートに依存しない。予約状態 `Settled` 連携は
/// app 層が `BookingSettlementPort` ACL 経由で行う。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Invoice {
    invoice_id: InvoiceId,
    invoice_number: String,
    booking_id: BillingBookingId,
    charge_total: Money,
    tax_rate: Decimal,
    tax_amount: Money,
    total_amount: Money,
    payment_status: PaymentStatus,
    issued_at: Option<DateTime<Utc>>,
    due_date: NaiveDate,
    line_items: Vec<InvoiceLineItem>,
    payments: Vec<Payment>,
}

impl Invoice {
    /// 確定料金から精算書を発行する（US23・Pending）。
    ///
    /// 請求金額 ＝ 確定料金（割引後）＋ 消費税（確定料金 × 税率・円未満四捨五入）。
    ///
    /// # Errors
    ///
    /// 精算書番号が空なら `EmptyInvoiceNumber`、通貨不一致は `CurrencyMismatch`。
    #[allow(clippy::too_many_arguments)]
    pub fn issue(
        invoice_id: InvoiceId,
        invoice_number: impl Into<String>,
        booking_id: BillingBookingId,
        charge_total: Money,
        tax_rate: Decimal,
        issued_at: DateTime<Utc>,
        due_date: NaiveDate,
        line_items: Vec<InvoiceLineItem>,
    ) -> Result<Self, BillingError> {
        let invoice_number = invoice_number.into();
        if invoice_number.trim().is_empty() {
            return Err(BillingError::EmptyInvoiceNumber);
        }
        let tax_amount = calculate_tax(charge_total, tax_rate);
        let total_amount = charge_total.add(&tax_amount)?;
        Ok(Self {
            invoice_id,
            invoice_number,
            booking_id,
            charge_total,
            tax_rate,
            tax_amount,
            total_amount,
            payment_status: PaymentStatus::Pending,
            issued_at: Some(issued_at),
            due_date,
            line_items,
            payments: Vec::new(),
        })
    }

    /// 永続化済みデータから再構築する。
    #[must_use]
    #[allow(clippy::too_many_arguments)]
    pub fn reconstruct(
        invoice_id: InvoiceId,
        invoice_number: String,
        booking_id: BillingBookingId,
        charge_total: Money,
        tax_rate: Decimal,
        tax_amount: Money,
        total_amount: Money,
        payment_status: PaymentStatus,
        issued_at: Option<DateTime<Utc>>,
        due_date: NaiveDate,
        line_items: Vec<InvoiceLineItem>,
        payments: Vec<Payment>,
    ) -> Self {
        Self {
            invoice_id,
            invoice_number,
            booking_id,
            charge_total,
            tax_rate,
            tax_amount,
            total_amount,
            payment_status,
            issued_at,
            due_date,
            line_items,
            payments,
        }
    }

    /// 入金を確認する（US23・Pending/Overdue → Confirmed・支払記録を追加）。
    ///
    /// # Errors
    ///
    /// 既に入金確認済みなら `AlreadyPaid`。
    pub fn confirm_payment(&mut self, payment: Payment) -> Result<(), BillingError> {
        if matches!(self.payment_status, PaymentStatus::Confirmed) {
            return Err(BillingError::AlreadyPaid);
        }
        self.payments.push(payment);
        self.payment_status = PaymentStatus::Confirmed;
        Ok(())
    }

    /// 支払期限を超過していれば未払い（Overdue）に遷移する（US23）。
    ///
    /// 未入金（Pending）かつ基準日が支払期限を過ぎている場合のみ Overdue にする。純粋な判定で
    /// 現在日付は app 層から渡す。
    ///
    /// # Returns
    ///
    /// Overdue へ遷移した場合 `true`。
    pub fn mark_overdue(&mut self, as_of: NaiveDate) -> bool {
        if matches!(self.payment_status, PaymentStatus::Pending) && as_of > self.due_date {
            self.payment_status = PaymentStatus::Overdue;
            return true;
        }
        false
    }

    /// 精算書 ID。
    #[must_use]
    pub fn invoice_id(&self) -> &InvoiceId {
        &self.invoice_id
    }

    /// 精算書番号（業務キー）。
    #[must_use]
    pub fn invoice_number(&self) -> &str {
        &self.invoice_number
    }

    /// 予約 ID。
    #[must_use]
    pub fn booking_id(&self) -> &BillingBookingId {
        &self.booking_id
    }

    /// 確定料金（割引後・税抜）。
    #[must_use]
    pub fn charge_total(&self) -> Money {
        self.charge_total
    }

    /// 税率。
    #[must_use]
    pub fn tax_rate(&self) -> Decimal {
        self.tax_rate
    }

    /// 消費税額。
    #[must_use]
    pub fn tax_amount(&self) -> Money {
        self.tax_amount
    }

    /// 請求金額（税込合計）。
    #[must_use]
    pub fn total_amount(&self) -> Money {
        self.total_amount
    }

    /// 支払状態。
    #[must_use]
    pub fn payment_status(&self) -> PaymentStatus {
        self.payment_status
    }

    /// 発行日時。
    #[must_use]
    pub fn issued_at(&self) -> Option<DateTime<Utc>> {
        self.issued_at
    }

    /// 支払期限。
    #[must_use]
    pub fn due_date(&self) -> NaiveDate {
        self.due_date
    }

    /// 明細一覧。
    #[must_use]
    pub fn line_items(&self) -> &[InvoiceLineItem] {
        &self.line_items
    }

    /// 支払記録一覧。
    #[must_use]
    pub fn payments(&self) -> &[Payment] {
        &self.payments
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::value_objects::Currency;

    fn invoice(charge: i64) -> Invoice {
        Invoice::issue(
            InvoiceId::generate(),
            "INV-0001",
            BillingBookingId::parse("BKG-1").unwrap(),
            Money::jpy(Decimal::from(charge)),
            DEFAULT_TAX_RATE,
            DateTime::from_timestamp(1_700_000_000, 0).unwrap(),
            NaiveDate::from_ymd_opt(2026, 6, 30).unwrap(),
            vec![InvoiceLineItem::new(
                "輸送料金",
                Money::jpy(Decimal::from(charge)),
                1,
            )],
        )
        .unwrap()
    }

    #[test]
    fn 確定料金から精算書を発行し消費税10パーセントが加算される() {
        // 確定料金 170,000 → 税 17,000 → 請求 187,000。
        let inv = invoice(170_000);
        assert_eq!(inv.charge_total(), Money::jpy(Decimal::from(170_000)));
        assert_eq!(inv.tax_amount(), Money::jpy(Decimal::from(17_000)));
        assert_eq!(inv.total_amount(), Money::jpy(Decimal::from(187_000)));
        assert_eq!(inv.payment_status(), PaymentStatus::Pending);
    }

    #[test]
    fn 消費税は円未満を四捨五入する() {
        // 100,005 × 0.10 = 10,000.5 → 10,001（四捨五入）。
        let tax = calculate_tax(Money::jpy(Decimal::from(100_005)), DEFAULT_TAX_RATE);
        assert_eq!(tax, Money::jpy(Decimal::from(10_001)));
    }

    #[test]
    fn 精算書番号が空だとエラー() {
        let result = Invoice::issue(
            InvoiceId::generate(),
            "  ",
            BillingBookingId::parse("BKG-1").unwrap(),
            Money::jpy(Decimal::from(1000)),
            DEFAULT_TAX_RATE,
            DateTime::from_timestamp(1_700_000_000, 0).unwrap(),
            NaiveDate::from_ymd_opt(2026, 6, 30).unwrap(),
            vec![],
        );
        assert_eq!(result, Err(BillingError::EmptyInvoiceNumber));
    }

    #[test]
    fn 入金確認で確定になり支払記録が追加される() {
        let mut inv = invoice(170_000);
        let payment = Payment::new(
            Money::jpy(Decimal::from(187_000)),
            DateTime::from_timestamp(1_700_100_000, 0).unwrap(),
            PaymentMethod::BankTransfer,
            Some("TXN-001".to_string()),
        );
        inv.confirm_payment(payment).unwrap();
        assert_eq!(inv.payment_status(), PaymentStatus::Confirmed);
        assert_eq!(inv.payments().len(), 1);
    }

    #[test]
    fn 入金確認済みへの再入金確認は拒否される() {
        let mut inv = invoice(170_000);
        let payment = || {
            Payment::new(
                Money::zero(Currency::Jpy),
                DateTime::from_timestamp(1_700_100_000, 0).unwrap(),
                PaymentMethod::BankTransfer,
                None,
            )
        };
        inv.confirm_payment(payment()).unwrap();
        assert_eq!(
            inv.confirm_payment(payment()),
            Err(BillingError::AlreadyPaid)
        );
    }

    #[test]
    fn 支払期限超過で未払いになるが期限内や入金済みは変わらない() {
        // 期限 6/30・基準 7/1 → Overdue。
        let mut inv = invoice(170_000);
        assert!(inv.mark_overdue(NaiveDate::from_ymd_opt(2026, 7, 1).unwrap()));
        assert_eq!(inv.payment_status(), PaymentStatus::Overdue);

        // 期限内は変わらない。
        let mut inv2 = invoice(170_000);
        assert!(!inv2.mark_overdue(NaiveDate::from_ymd_opt(2026, 6, 30).unwrap()));
        assert_eq!(inv2.payment_status(), PaymentStatus::Pending);

        // 入金済みは期限を過ぎても Overdue にしない。
        let mut inv3 = invoice(170_000);
        inv3.confirm_payment(Payment::new(
            Money::jpy(Decimal::from(187_000)),
            DateTime::from_timestamp(1_700_100_000, 0).unwrap(),
            PaymentMethod::BankTransfer,
            None,
        ))
        .unwrap();
        assert!(!inv3.mark_overdue(NaiveDate::from_ymd_opt(2026, 7, 1).unwrap()));
        assert_eq!(inv3.payment_status(), PaymentStatus::Confirmed);
    }
}
