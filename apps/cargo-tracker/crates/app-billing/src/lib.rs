//! Billing Context のアプリケーション層。
//!
//! 輸送料金の算出（US21）と法人割引の適用（US22）ユースケースを提供する。輸送実績の参照
//! （Booking/Handling/Routing）と割引率の取得（Shipper）は ACL ポート経由で行い、
//! `domain-billing` に他 BC の依存を持ち込まない（BC 独立・ADR-0007 パターン踏襲）。

use async_trait::async_trait;
use domain_billing::{
    AdjustmentReason, BillingBookingId, BillingRepositoryError, ChargeAdjustment, ChargeStatus,
    DiscountRate, FreightCharge, FreightChargeId, FreightChargeRepository, Money,
};
use rust_decimal::Decimal;

/// 料金算定の係数（名前付き定数・金額のリグレッションを単体テストで固定する。IT6 Try 教訓）。
mod rates {
    use rust_decimal::Decimal;

    /// 重量 1kg あたりの基本単価（JPY）。
    pub const RATE_PER_KG: Decimal = Decimal::from_parts(100, 0, 0, false, 0); // 100
    /// 距離 1km あたりの基本単価（JPY）。
    pub const RATE_PER_KM: Decimal = Decimal::from_parts(20, 0, 0, false, 0); // 20

    /// 一般貨物の係数（1.0）。
    pub const FACTOR_GENERAL: Decimal = Decimal::from_parts(10, 0, 0, false, 1); // 1.0
    /// 危険物の係数（1.5）。
    pub const FACTOR_HAZARDOUS: Decimal = Decimal::from_parts(15, 0, 0, false, 1); // 1.5
    /// 冷凍・冷蔵の係数（1.3）。
    pub const FACTOR_REFRIGERATED: Decimal = Decimal::from_parts(13, 0, 0, false, 1); // 1.3
}

/// 料金ユースケースのエラー。
#[derive(Debug, thiserror::Error)]
pub enum BillingServiceError {
    /// 対象予約が見つからない。
    #[error("対象の予約が見つかりません: {0}")]
    NotFound(String),
    /// 引取済でない予約に対する料金算出（US21・前提条件違反）。
    #[error("引取済の予約のみ料金算出できます: {0}")]
    NotDeliverable(String),
    /// 確定済み料金の再算出（US21・確定後は変更不可）。
    #[error("確定済みの料金は再算出できません: {0}")]
    AlreadyConfirmed(String),
    /// 入力が不正。
    #[error("入力が不正です: {0}")]
    InvalidInput(String),
    /// 永続化・外部連携の失敗。
    #[error("処理に失敗しました: {0}")]
    Backend(String),
}

impl From<BillingRepositoryError> for BillingServiceError {
    fn from(e: BillingRepositoryError) -> Self {
        Self::Backend(e.to_string())
    }
}

/// 輸送実績（ACL 出力・Booking/Handling/Routing を Billing 向けに集約した DTO）。
///
/// BC 独立のため他コンテキストの型は持ち込まず、プリミティブで受け渡す。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TransportActuals {
    /// 荷主 ID（割引率取得に使用）。
    pub shipper_id: String,
    /// 貨物種別（"GENERAL"/"HAZARDOUS"/"REFRIGERATED"）。
    pub cargo_type: String,
    /// 総重量（kg）。
    pub weight_kg: Decimal,
    /// 総輸送距離（km・経路実績から算定）。
    pub distance_km: Decimal,
    /// 引取済（料金算出の前提・US21）。
    pub is_delivered: bool,
}

/// 輸送実績参照 ACL（Billing 側ポート）。
///
/// Booking/Handling/Routing の実績を Billing 向けに集約して返す。実装は composition root で
/// 各コンテキストをラップする（BC 独立）。
#[async_trait]
pub trait BookingActualsProvider: Send + Sync {
    /// 予約 ID から輸送実績を取得する（存在しなければ `None`）。
    async fn find_actuals(
        &self,
        booking_id: &str,
    ) -> Result<Option<TransportActuals>, BillingServiceError>;
}

/// 荷主割引率取得 ACL（Billing 側ポート）。
///
/// 荷主 ID から契約割引率（法人のみ・個人は 0%）を取得する。Shipper Context の
/// `DiscountRate` をプリミティブ（`Decimal`）で受け渡し、Billing は自前の `DiscountRate` を構築する。
#[async_trait]
pub trait ShipperDiscountProvider: Send + Sync {
    /// 荷主 ID から割引率（0.0〜0.3）を取得する。個人・未契約は 0。
    async fn find_discount_rate(&self, shipper_id: &str) -> Result<Decimal, BillingServiceError>;
}

/// 料金調整の入力（US21・例外時の減額/補償）。
#[derive(Debug, Clone)]
pub struct AdjustmentInput {
    /// 調整理由。
    pub reason: AdjustmentReason,
    /// 控除金額（JPY）。
    pub amount: Decimal,
}

/// 料金算出の入力（US21/US22）。
#[derive(Debug, Clone)]
pub struct CalculateFreightInput {
    /// 対象予約 ID（引取済であること）。
    pub booking_id: String,
    /// 例外時の料金調整（任意）。
    pub adjustments: Vec<AdjustmentInput>,
}

/// 料金算出の結果。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CalculateFreightOutcome {
    /// 発行された料金 ID。
    pub charge_id: String,
    /// 基本料金（JPY）。
    pub base_amount: Decimal,
    /// 割引額（JPY・割引なしは 0）。
    pub discount_amount: Decimal,
    /// 合計金額（JPY）。
    pub total_amount: Decimal,
}

/// 貨物種別文字列から料金係数を求める（未知は一般貨物扱い）。
fn cargo_factor(cargo_type: &str) -> Decimal {
    match cargo_type {
        "HAZARDOUS" => rates::FACTOR_HAZARDOUS,
        "REFRIGERATED" => rates::FACTOR_REFRIGERATED,
        _ => rates::FACTOR_GENERAL,
    }
}

/// 輸送実績から基本料金を算定する純粋関数（金額のリグレッションを単体テストで固定）。
///
/// 基本料金 = (重量kg × 単価 + 距離km × 単価) × 貨物種別係数
#[must_use]
pub fn calculate_base_amount(actuals: &TransportActuals) -> Money {
    let by_weight = actuals.weight_kg * rates::RATE_PER_KG;
    let by_distance = actuals.distance_km * rates::RATE_PER_KM;
    let base = (by_weight + by_distance) * cargo_factor(&actuals.cargo_type);
    // 円未満を丸める（JPY は最小単位 1 円・ADR-0010）。
    Money::jpy(base).rounded()
}

/// 輸送料金算出ユースケース（US21/US22）。
pub struct CalculateFreightService<R, A, D>
where
    R: FreightChargeRepository,
    A: BookingActualsProvider,
    D: ShipperDiscountProvider,
{
    repository: R,
    actuals_provider: A,
    discount_provider: D,
}

impl<R, A, D> CalculateFreightService<R, A, D>
where
    R: FreightChargeRepository,
    A: BookingActualsProvider,
    D: ShipperDiscountProvider,
{
    /// サービスを生成する。
    pub fn new(repository: R, actuals_provider: A, discount_provider: D) -> Self {
        Self {
            repository,
            actuals_provider,
            discount_provider,
        }
    }

    /// 輸送料金を算出し下書き（Draft）として保存する（US21/US22）。
    ///
    /// 手順:
    /// 1. 輸送実績を ACL で取得（無ければ `NotFound`・引取済でなければ `NotDeliverable`）
    /// 2. 基本料金を算定し `FreightCharge` を生成
    /// 3. 例外調整（減額・補償）を適用
    /// 4. 荷主割引率を ACL で取得し適用（法人のみ・個人は 0%）
    /// 5. 保存し、料金 ID・基本料金・割引額・合計を返す
    ///
    /// # Errors
    ///
    /// 実績なし `NotFound`、未引取 `NotDeliverable`、入力不正 `InvalidInput`、永続化失敗 `Backend`。
    pub async fn calculate(
        &self,
        input: CalculateFreightInput,
    ) -> Result<CalculateFreightOutcome, BillingServiceError> {
        let actuals = self
            .actuals_provider
            .find_actuals(&input.booking_id)
            .await?
            .ok_or_else(|| BillingServiceError::NotFound(input.booking_id.clone()))?;
        if !actuals.is_delivered {
            return Err(BillingServiceError::NotDeliverable(
                input.booking_id.clone(),
            ));
        }

        let booking_id = BillingBookingId::parse(&input.booking_id)
            .map_err(|e| BillingServiceError::InvalidInput(e.to_string()))?;
        // 再算出は予約単位で冪等（1 予約 1 料金）。既存の下書きがあれば料金 ID を再利用し、
        // 新規採番で redirect 先が DB に無い（404）不整合を防ぐ。確定済みは再算出不可。
        let charge_id = match self.repository.find_by_booking_id(&booking_id).await? {
            Some(existing) if matches!(existing.status(), ChargeStatus::Confirmed) => {
                return Err(BillingServiceError::AlreadyConfirmed(
                    input.booking_id.clone(),
                ));
            }
            Some(existing) => existing.charge_id().clone(),
            None => FreightChargeId::generate(),
        };
        let base_amount = calculate_base_amount(&actuals);
        let mut charge = FreightCharge::create(charge_id, booking_id, base_amount);

        for adj in &input.adjustments {
            charge
                .add_adjustment(ChargeAdjustment::new(adj.reason, Money::jpy(adj.amount)))
                .map_err(|e| BillingServiceError::InvalidInput(e.to_string()))?;
        }

        let rate_value = self
            .discount_provider
            .find_discount_rate(&actuals.shipper_id)
            .await?;
        let rate = DiscountRate::new(rate_value)
            .map_err(|e| BillingServiceError::InvalidInput(e.to_string()))?;
        charge
            .apply_discount(rate)
            .map_err(|e| BillingServiceError::InvalidInput(e.to_string()))?;

        self.repository.save(&charge).await?;

        let discount_amount = charge
            .discount()
            .map_or(Decimal::ZERO, |d| d.discount_amount().amount());
        let total = charge
            .total()
            .map_err(|e| BillingServiceError::Backend(e.to_string()))?;
        Ok(CalculateFreightOutcome {
            charge_id: charge.charge_id().as_str().to_string(),
            base_amount: charge.base_amount().amount(),
            discount_amount,
            total_amount: total.amount(),
        })
    }

    /// 料金を確定する（US21・Draft → Confirmed）。
    ///
    /// # Errors
    ///
    /// 料金なし `NotFound`、確定不可・永続化失敗は `Backend`/`InvalidInput`。
    pub async fn confirm(&self, charge_id: &str) -> Result<(), BillingServiceError> {
        let id = FreightChargeId::from_string(charge_id);
        let mut charge = self
            .repository
            .find_by_id(&id)
            .await?
            .ok_or_else(|| BillingServiceError::NotFound(charge_id.to_string()))?;
        charge
            .confirm()
            .map_err(|e| BillingServiceError::InvalidInput(e.to_string()))?;
        self.repository.save(&charge).await?;
        Ok(())
    }
}

// ===== 精算（US23）=====

use chrono::{NaiveDate, Utc};
use domain_billing::{
    DEFAULT_TAX_RATE, Invoice, InvoiceId, InvoiceLineItem, InvoiceRepository, Payment,
    PaymentMethod,
};

/// 支払期限（発行日からの日数・US23）。
const PAYMENT_TERM_DAYS: i64 = 30;

/// 決済機関の入金確認結果（ACL 出力）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PaymentConfirmation {
    /// 取引参照番号。
    pub transaction_reference: String,
    /// 支払方法。
    pub payment_method: String,
}

/// 決済機関連携 ACL（Billing 側ポート・US23）。外部決済機関との入金確認を隠蔽する。
#[async_trait]
pub trait PaymentGatewayPort: Send + Sync {
    /// 精算書番号・請求金額で入金を確認する。失敗時は `Backend`。
    async fn confirm_payment(
        &self,
        invoice_number: &str,
        amount: rust_decimal::Decimal,
    ) -> Result<PaymentConfirmation, BillingServiceError>;
}

/// 予約精算連携 ACL（Billing → Booking・US23）。入金確認後に予約を `Settled` へ遷移させる。
#[async_trait]
pub trait BookingSettlementPort: Send + Sync {
    /// 予約を精算済にする。対象が無い/遷移不可は `Backend`。
    async fn settle(&self, booking_id: &str) -> Result<(), BillingServiceError>;
}

/// 精算通知 ACL（送信＝記録・US23）。
#[async_trait]
pub trait InvoiceNotificationPort: Send + Sync {
    /// 精算書発行を荷主へ通知する。
    async fn notify_invoice_issued(
        &self,
        booking_id: &str,
        invoice_number: &str,
        amount: rust_decimal::Decimal,
    ) -> Result<(), BillingServiceError>;

    /// 支払期限超過を経理へ通知する。
    async fn notify_payment_overdue(
        &self,
        booking_id: &str,
        invoice_number: &str,
    ) -> Result<(), BillingServiceError>;

    /// 精算完了を荷主へ通知する（入金確認後・US23）。
    async fn notify_settlement_completed(
        &self,
        booking_id: &str,
        invoice_number: &str,
    ) -> Result<(), BillingServiceError>;
}

/// 精算書発行の結果。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct GenerateInvoiceOutcome {
    /// 発行された精算書番号。
    pub invoice_number: String,
    /// 請求金額（税込）。
    pub total_amount: rust_decimal::Decimal,
    /// 消費税額。
    pub tax_amount: rust_decimal::Decimal,
}

/// 精算書発行ユースケース（US23）。確定料金 → 精算書生成・荷主通知。
pub struct GenerateInvoiceService<C, I, N>
where
    C: FreightChargeRepository,
    I: InvoiceRepository,
    N: InvoiceNotificationPort,
{
    charge_repo: C,
    invoice_repo: I,
    notifications: N,
}

impl<C, I, N> GenerateInvoiceService<C, I, N>
where
    C: FreightChargeRepository,
    I: InvoiceRepository,
    N: InvoiceNotificationPort,
{
    /// サービスを生成する。
    pub fn new(charge_repo: C, invoice_repo: I, notifications: N) -> Self {
        Self {
            charge_repo,
            invoice_repo,
            notifications,
        }
    }

    /// 確定料金から精算書を発行する（US23）。
    ///
    /// 手順:
    /// 1. 料金 ID で確定料金を取得（未確定は `InvalidInput`）
    /// 2. 予約単位で冪等（既存精算書があればその番号を返す）
    /// 3. 確定料金＋消費税で `Invoice` を発行・保存
    /// 4. 荷主へ精算書発行を通知
    ///
    /// # Errors
    ///
    /// 料金なし `NotFound`、未確定 `InvalidInput`、永続化・通知失敗 `Backend`。
    pub async fn generate(
        &self,
        charge_id: &str,
        issued_on: NaiveDate,
    ) -> Result<GenerateInvoiceOutcome, BillingServiceError> {
        let charge = self
            .charge_repo
            .find_by_id(&FreightChargeId::from_string(charge_id))
            .await?
            .ok_or_else(|| BillingServiceError::NotFound(charge_id.to_string()))?;
        if !matches!(charge.status(), ChargeStatus::Confirmed) {
            return Err(BillingServiceError::InvalidInput(
                "確定済みの料金のみ精算書を発行できます".to_string(),
            ));
        }

        // 予約単位で冪等（1 予約 1 精算書）。
        if let Some(existing) = self
            .invoice_repo
            .find_by_booking_id(charge.booking_id())
            .await?
        {
            return Ok(GenerateInvoiceOutcome {
                invoice_number: existing.invoice_number().to_string(),
                total_amount: existing.total_amount().amount(),
                tax_amount: existing.tax_amount().amount(),
            });
        }

        let charge_total = charge
            .total()
            .map_err(|e| BillingServiceError::Backend(e.to_string()))?;
        // 精算書番号は VARCHAR(30) に収まるよう UUID 先頭 24 桁を用いる（INV- + 24 = 28 文字）。
        let uuid_hex = uuid::Uuid::new_v4().simple().to_string();
        let invoice_number = format!("INV-{}", &uuid_hex[..24]);
        let due_date = issued_on + chrono::Duration::days(PAYMENT_TERM_DAYS);
        let line_items = vec![InvoiceLineItem::new("輸送料金（割引後）", charge_total, 1)];
        let invoice = Invoice::issue(
            InvoiceId::generate(),
            invoice_number.clone(),
            charge.booking_id().clone(),
            charge_total,
            DEFAULT_TAX_RATE,
            Utc::now(),
            due_date,
            line_items,
        )
        .map_err(|e| BillingServiceError::InvalidInput(e.to_string()))?;
        self.invoice_repo.save(&invoice).await?;
        self.notifications
            .notify_invoice_issued(
                charge.booking_id().as_str(),
                &invoice_number,
                invoice.total_amount().amount(),
            )
            .await?;

        Ok(GenerateInvoiceOutcome {
            invoice_number,
            total_amount: invoice.total_amount().amount(),
            tax_amount: invoice.tax_amount().amount(),
        })
    }
}

/// 入金確認ユースケース（US23）。決済機関連携→入金記録→精算完了→予約 Settled→荷主通知。
pub struct ConfirmPaymentService<I, P, B, N>
where
    I: InvoiceRepository,
    P: PaymentGatewayPort,
    B: BookingSettlementPort,
    N: InvoiceNotificationPort,
{
    invoice_repo: I,
    gateway: P,
    settlement: B,
    notifications: N,
}

impl<I, P, B, N> ConfirmPaymentService<I, P, B, N>
where
    I: InvoiceRepository,
    P: PaymentGatewayPort,
    B: BookingSettlementPort,
    N: InvoiceNotificationPort,
{
    /// サービスを生成する。
    pub fn new(invoice_repo: I, gateway: P, settlement: B, notifications: N) -> Self {
        Self {
            invoice_repo,
            gateway,
            settlement,
            notifications,
        }
    }

    /// 入金を確認し精算を完了する（US23）。
    ///
    /// # Errors
    ///
    /// 精算書なし `NotFound`、既払い `InvalidInput`、決済・永続化・予約連携失敗 `Backend`。
    pub async fn confirm(&self, invoice_number: &str) -> Result<(), BillingServiceError> {
        let mut invoice = self
            .invoice_repo
            .find_by_number(invoice_number)
            .await?
            .ok_or_else(|| BillingServiceError::NotFound(invoice_number.to_string()))?;
        let confirmation = self
            .gateway
            .confirm_payment(invoice_number, invoice.total_amount().amount())
            .await?;
        let payment = Payment::new(
            invoice.total_amount(),
            Utc::now(),
            PaymentMethod::from_str_or_bank_transfer(&confirmation.payment_method),
            Some(confirmation.transaction_reference),
        );
        invoice
            .confirm_payment(payment)
            .map_err(|e| BillingServiceError::InvalidInput(e.to_string()))?;
        let booking_id = invoice.booking_id().as_str().to_string();
        self.invoice_repo.save(&invoice).await?;
        // 予約を精算済へ（BookingSettlementPort ACL・BC 独立）。
        self.settlement.settle(&booking_id).await?;
        // 精算完了を荷主へ通知する（US23）。
        self.notifications
            .notify_settlement_completed(&booking_id, invoice_number)
            .await?;
        Ok(())
    }
}

/// 支払期限超過チェックユースケース（US23・未払い通知）。
///
/// 基準日時点で支払期限を過ぎた未入金の精算書を `Overdue` にし、経理担当者へ未払い通知する。
/// 定期実行（バッチ）から基準日を渡して駆動する。
pub struct CheckOverdueService<I, N>
where
    I: InvoiceRepository,
    N: InvoiceNotificationPort,
{
    invoice_repo: I,
    notifications: N,
}

impl<I, N> CheckOverdueService<I, N>
where
    I: InvoiceRepository,
    N: InvoiceNotificationPort,
{
    /// サービスを生成する。
    pub fn new(invoice_repo: I, notifications: N) -> Self {
        Self {
            invoice_repo,
            notifications,
        }
    }

    /// 基準日で期限超過の精算書を Overdue にし未払い通知する（US23）。
    ///
    /// # Returns
    ///
    /// 新たに Overdue にした件数。
    ///
    /// # Errors
    ///
    /// 永続化・通知失敗は `Backend`。
    pub async fn check(&self, as_of: NaiveDate) -> Result<usize, BillingServiceError> {
        let invoices = self.invoice_repo.find_all().await?;
        let mut overdue = 0;
        for mut invoice in invoices {
            if invoice.mark_overdue(as_of) {
                self.invoice_repo.save(&invoice).await?;
                self.notifications
                    .notify_payment_overdue(invoice.booking_id().as_str(), invoice.invoice_number())
                    .await?;
                overdue += 1;
            }
        }
        Ok(overdue)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use domain_billing::ChargeStatus;
    use mockall::mock;
    use rust_decimal::prelude::*;

    mock! {
        Repo {}
        #[async_trait]
        impl FreightChargeRepository for Repo {
            async fn save(&self, charge: &FreightCharge) -> Result<(), BillingRepositoryError>;
            async fn find_by_id(
                &self,
                id: &FreightChargeId,
            ) -> Result<Option<FreightCharge>, BillingRepositoryError>;
            async fn find_by_booking_id(
                &self,
                booking_id: &BillingBookingId,
            ) -> Result<Option<FreightCharge>, BillingRepositoryError>;
            async fn find_all(&self) -> Result<Vec<FreightCharge>, BillingRepositoryError>;
        }
    }

    mock! {
        Actuals {}
        #[async_trait]
        impl BookingActualsProvider for Actuals {
            async fn find_actuals(
                &self,
                booking_id: &str,
            ) -> Result<Option<TransportActuals>, BillingServiceError>;
        }
    }

    mock! {
        Discount {}
        #[async_trait]
        impl ShipperDiscountProvider for Discount {
            async fn find_discount_rate(
                &self,
                shipper_id: &str,
            ) -> Result<Decimal, BillingServiceError>;
        }
    }

    fn actuals(delivered: bool, cargo_type: &str) -> TransportActuals {
        TransportActuals {
            shipper_id: "SHP-1".to_string(),
            cargo_type: cargo_type.to_string(),
            weight_kg: Decimal::from(1000),
            distance_km: Decimal::from(5000),
            is_delivered: delivered,
        }
    }

    #[test]
    fn 基本料金は重量と距離と貨物種別係数で算定される() {
        // 一般: (1000×100 + 5000×20) × 1.0 = 200,000
        let general = calculate_base_amount(&actuals(true, "GENERAL"));
        assert_eq!(general, Money::jpy(Decimal::from(200_000)));
        // 危険物: 200,000 × 1.5 = 300,000
        let hazardous = calculate_base_amount(&actuals(true, "HAZARDOUS"));
        assert_eq!(hazardous, Money::jpy(Decimal::from(300_000)));
        // 冷凍: 200,000 × 1.3 = 260,000
        let refrigerated = calculate_base_amount(&actuals(true, "REFRIGERATED"));
        assert_eq!(refrigerated, Money::jpy(Decimal::from(260_000)));
    }

    #[tokio::test]
    async fn 引取済予約の料金を算出し個人荷主は割引なしで確定前の下書きになる() {
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id().returning(|_| Ok(None));
        repo.expect_save()
            .times(1)
            .withf(|c| c.status() == ChargeStatus::Draft && c.discount().is_none())
            .returning(|_| Ok(()));
        let mut ap = MockActuals::new();
        ap.expect_find_actuals()
            .returning(|_| Ok(Some(actuals(true, "GENERAL"))));
        let mut dp = MockDiscount::new();
        dp.expect_find_discount_rate()
            .returning(|_| Ok(Decimal::ZERO));

        let service = CalculateFreightService::new(repo, ap, dp);
        let outcome = service
            .calculate(CalculateFreightInput {
                booking_id: "BKG-1".to_string(),
                adjustments: vec![],
            })
            .await
            .expect("算出できるはず");
        assert!(outcome.charge_id.starts_with("FRC-"));
        assert_eq!(outcome.base_amount, Decimal::from(200_000));
        assert_eq!(outcome.discount_amount, Decimal::ZERO);
        assert_eq!(outcome.total_amount, Decimal::from(200_000));
    }

    #[tokio::test]
    async fn 法人荷主は割引率が適用され割引後金額になる() {
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id().returning(|_| Ok(None));
        repo.expect_save()
            .times(1)
            .withf(|c| c.discount().is_some())
            .returning(|_| Ok(()));
        let mut ap = MockActuals::new();
        ap.expect_find_actuals()
            .returning(|_| Ok(Some(actuals(true, "GENERAL"))));
        let mut dp = MockDiscount::new();
        // 法人割引 10%。
        dp.expect_find_discount_rate()
            .returning(|_| Ok(Decimal::from_str("0.10").unwrap()));

        let service = CalculateFreightService::new(repo, ap, dp);
        let outcome = service
            .calculate(CalculateFreightInput {
                booking_id: "BKG-1".to_string(),
                adjustments: vec![],
            })
            .await
            .expect("算出できるはず");
        assert_eq!(outcome.base_amount, Decimal::from(200_000));
        assert_eq!(outcome.discount_amount, Decimal::from(20_000));
        assert_eq!(outcome.total_amount, Decimal::from(180_000));
    }

    #[tokio::test]
    async fn 例外調整は合計から控除される() {
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id().returning(|_| Ok(None));
        repo.expect_save().times(1).returning(|_| Ok(()));
        let mut ap = MockActuals::new();
        ap.expect_find_actuals()
            .returning(|_| Ok(Some(actuals(true, "GENERAL"))));
        let mut dp = MockDiscount::new();
        dp.expect_find_discount_rate()
            .returning(|_| Ok(Decimal::ZERO));

        let service = CalculateFreightService::new(repo, ap, dp);
        let outcome = service
            .calculate(CalculateFreightInput {
                booking_id: "BKG-1".to_string(),
                adjustments: vec![AdjustmentInput {
                    reason: AdjustmentReason::DelayReduction,
                    amount: Decimal::from(15_000),
                }],
            })
            .await
            .expect("算出できるはず");
        assert_eq!(outcome.total_amount, Decimal::from(185_000));
    }

    #[tokio::test]
    async fn 引取済でない予約は料金算出できない() {
        let mut repo = MockRepo::new();
        repo.expect_save().never();
        let mut ap = MockActuals::new();
        ap.expect_find_actuals()
            .returning(|_| Ok(Some(actuals(false, "GENERAL"))));
        let dp = MockDiscount::new();

        let service = CalculateFreightService::new(repo, ap, dp);
        let result = service
            .calculate(CalculateFreightInput {
                booking_id: "BKG-1".to_string(),
                adjustments: vec![],
            })
            .await;
        assert!(matches!(
            result,
            Err(BillingServiceError::NotDeliverable(_))
        ));
    }

    #[tokio::test]
    async fn 再算出は既存の料金idを再利用する() {
        let existing = FreightCharge::create(
            FreightChargeId::from_string("FRC-existing"),
            BillingBookingId::parse("BKG-1").unwrap(),
            Money::jpy(Decimal::from(200_000)),
        );
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id()
            .returning(move |_| Ok(Some(existing.clone())));
        // 保存される料金 ID は既存の FRC-existing（新規採番しない）。
        repo.expect_save()
            .times(1)
            .withf(|c| c.charge_id().as_str() == "FRC-existing")
            .returning(|_| Ok(()));
        let mut ap = MockActuals::new();
        ap.expect_find_actuals()
            .returning(|_| Ok(Some(actuals(true, "GENERAL"))));
        let mut dp = MockDiscount::new();
        dp.expect_find_discount_rate()
            .returning(|_| Ok(Decimal::ZERO));

        let service = CalculateFreightService::new(repo, ap, dp);
        let outcome = service
            .calculate(CalculateFreightInput {
                booking_id: "BKG-1".to_string(),
                adjustments: vec![],
            })
            .await
            .expect("再算出できるはず");
        assert_eq!(outcome.charge_id, "FRC-existing");
    }

    #[tokio::test]
    async fn 確定済み料金は再算出できない() {
        let mut confirmed = FreightCharge::create(
            FreightChargeId::from_string("FRC-confirmed"),
            BillingBookingId::parse("BKG-1").unwrap(),
            Money::jpy(Decimal::from(200_000)),
        );
        confirmed.confirm().unwrap();
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id()
            .returning(move |_| Ok(Some(confirmed.clone())));
        repo.expect_save().never();
        let mut ap = MockActuals::new();
        ap.expect_find_actuals()
            .returning(|_| Ok(Some(actuals(true, "GENERAL"))));
        let mut dp = MockDiscount::new();
        dp.expect_find_discount_rate()
            .returning(|_| Ok(Decimal::ZERO));

        let service = CalculateFreightService::new(repo, ap, dp);
        let result = service
            .calculate(CalculateFreightInput {
                booking_id: "BKG-1".to_string(),
                adjustments: vec![],
            })
            .await;
        assert!(matches!(
            result,
            Err(BillingServiceError::AlreadyConfirmed(_))
        ));
    }

    #[tokio::test]
    async fn 実績が無い予約はnotfound() {
        let mut repo = MockRepo::new();
        repo.expect_save().never();
        let mut ap = MockActuals::new();
        ap.expect_find_actuals().returning(|_| Ok(None));
        let dp = MockDiscount::new();

        let service = CalculateFreightService::new(repo, ap, dp);
        let result = service
            .calculate(CalculateFreightInput {
                booking_id: "BKG-X".to_string(),
                adjustments: vec![],
            })
            .await;
        assert!(matches!(result, Err(BillingServiceError::NotFound(_))));
    }

    // ===== 精算（US23）=====

    use domain_billing::{FreightCharge, Invoice, InvoiceLineItem, Money};

    mock! {
        InvRepo {}
        #[async_trait]
        impl InvoiceRepository for InvRepo {
            async fn save(&self, invoice: &Invoice) -> Result<(), BillingRepositoryError>;
            async fn find_by_number(&self, n: &str) -> Result<Option<Invoice>, BillingRepositoryError>;
            async fn find_by_booking_id(&self, b: &BillingBookingId) -> Result<Option<Invoice>, BillingRepositoryError>;
            async fn find_all(&self) -> Result<Vec<Invoice>, BillingRepositoryError>;
        }
    }

    mock! {
        Gateway {}
        #[async_trait]
        impl PaymentGatewayPort for Gateway {
            async fn confirm_payment(&self, invoice_number: &str, amount: Decimal) -> Result<PaymentConfirmation, BillingServiceError>;
        }
    }

    mock! {
        Settlement {}
        #[async_trait]
        impl BookingSettlementPort for Settlement {
            async fn settle(&self, booking_id: &str) -> Result<(), BillingServiceError>;
        }
    }

    mock! {
        InvNotify {}
        #[async_trait]
        impl InvoiceNotificationPort for InvNotify {
            async fn notify_invoice_issued(&self, booking_id: &str, invoice_number: &str, amount: Decimal) -> Result<(), BillingServiceError>;
            async fn notify_payment_overdue(&self, booking_id: &str, invoice_number: &str) -> Result<(), BillingServiceError>;
            async fn notify_settlement_completed(&self, booking_id: &str, invoice_number: &str) -> Result<(), BillingServiceError>;
        }
    }

    fn confirmed_charge(total: i64) -> FreightCharge {
        let mut c = FreightCharge::create(
            FreightChargeId::from_string("FRC-1"),
            BillingBookingId::parse("BKG-1").unwrap(),
            Money::jpy(Decimal::from(total)),
        );
        c.confirm().unwrap();
        c
    }

    fn issued_invoice() -> Invoice {
        Invoice::issue(
            domain_billing::InvoiceId::from_string("INV-x"),
            "INV-0001",
            BillingBookingId::parse("BKG-1").unwrap(),
            Money::jpy(Decimal::from(170_000)),
            DEFAULT_TAX_RATE,
            Utc::now(),
            NaiveDate::from_ymd_opt(2026, 6, 30).unwrap(),
            vec![InvoiceLineItem::new(
                "輸送料金",
                Money::jpy(Decimal::from(170_000)),
                1,
            )],
        )
        .unwrap()
    }

    #[tokio::test]
    async fn 確定料金から精算書を発行し消費税込みで荷主へ通知する() {
        let mut cr = MockRepo::new();
        cr.expect_find_by_id()
            .returning(|_| Ok(Some(confirmed_charge(170_000))));
        let mut ir = MockInvRepo::new();
        ir.expect_find_by_booking_id().returning(|_| Ok(None));
        ir.expect_save().times(1).returning(|_| Ok(()));
        let mut n = MockInvNotify::new();
        n.expect_notify_invoice_issued()
            .times(1)
            .returning(|_, _, _| Ok(()));

        let service = GenerateInvoiceService::new(cr, ir, n);
        let outcome = service
            .generate("FRC-1", NaiveDate::from_ymd_opt(2026, 6, 1).unwrap())
            .await
            .expect("発行できるはず");
        assert!(outcome.invoice_number.starts_with("INV-"));
        assert_eq!(outcome.tax_amount, Decimal::from(17_000));
        assert_eq!(outcome.total_amount, Decimal::from(187_000));
    }

    #[tokio::test]
    async fn 未確定の料金からは精算書を発行できない() {
        let mut cr = MockRepo::new();
        cr.expect_find_by_id().returning(|_| {
            Ok(Some(FreightCharge::create(
                FreightChargeId::from_string("FRC-1"),
                BillingBookingId::parse("BKG-1").unwrap(),
                Money::jpy(Decimal::from(170_000)),
            )))
        });
        let mut ir = MockInvRepo::new();
        ir.expect_save().never();
        let n = MockInvNotify::new();

        let service = GenerateInvoiceService::new(cr, ir, n);
        let result = service
            .generate("FRC-1", NaiveDate::from_ymd_opt(2026, 6, 1).unwrap())
            .await;
        assert!(matches!(result, Err(BillingServiceError::InvalidInput(_))));
    }

    #[tokio::test]
    async fn 入金確認で精算完了し予約が精算済になる() {
        let mut ir = MockInvRepo::new();
        ir.expect_find_by_number()
            .returning(|_| Ok(Some(issued_invoice())));
        ir.expect_save()
            .times(1)
            .withf(|i| i.payment_status() == domain_billing::PaymentStatus::Confirmed)
            .returning(|_| Ok(()));
        let mut gw = MockGateway::new();
        gw.expect_confirm_payment().times(1).returning(|_, _| {
            Ok(PaymentConfirmation {
                transaction_reference: "TXN-001".to_string(),
                payment_method: "BANK_TRANSFER".to_string(),
            })
        });
        let mut st = MockSettlement::new();
        // 予約が Settled へ遷移する（BookingSettlementPort）。
        st.expect_settle()
            .times(1)
            .withf(|b| b == "BKG-1")
            .returning(|_| Ok(()));
        let mut n = MockInvNotify::new();
        // 精算完了を荷主へ通知する（US23）。
        n.expect_notify_settlement_completed()
            .times(1)
            .returning(|_, _| Ok(()));

        let service = ConfirmPaymentService::new(ir, gw, st, n);
        service
            .confirm("INV-0001")
            .await
            .expect("入金確認できるはず");
    }

    #[tokio::test]
    async fn 期限超過の未払い精算書を検出し経理へ未払い通知する() {
        // 期限 6/30・基準 7/1 → Overdue。
        let mut ir = MockInvRepo::new();
        ir.expect_find_all()
            .returning(|| Ok(vec![issued_invoice()]));
        ir.expect_save()
            .times(1)
            .withf(|i| i.payment_status() == domain_billing::PaymentStatus::Overdue)
            .returning(|_| Ok(()));
        let mut n = MockInvNotify::new();
        n.expect_notify_payment_overdue()
            .times(1)
            .returning(|_, _| Ok(()));

        let service = CheckOverdueService::new(ir, n);
        let count = service
            .check(NaiveDate::from_ymd_opt(2026, 7, 1).unwrap())
            .await
            .expect("チェックできるはず");
        assert_eq!(count, 1);
    }
}
