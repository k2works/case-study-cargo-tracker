//! Billing Context の ACL アダプター（composition 層）。
//!
//! `app-billing` の `BookingActualsProvider`／`ShipperDiscountProvider` を既存の Booking/Handling/
//! Routing・Shipper へ橋渡しする。ドメインクレート同士は依存せず、結線をここに閉じ込める（BC 独立）。
//! 輸送距離は経路（選択レグ数）ベースの暫定導出（distance カラムを持たないため。将来差し替え）。

use app_billing::{
    BillingServiceError, BookingActualsProvider, BookingSettlementPort, InvoiceNotificationPort,
    PaymentConfirmation, PaymentGatewayPort, ShipperDiscountProvider, TransportActuals,
};
use async_trait::async_trait;
use domain_booking::{BookingId, CargoRepository};
use rust_decimal::Decimal;
use sqlx::{PgPool, Row};
use std::sync::Arc;

/// 選択経路レグ 1 区間あたりの名目距離（km・暫定スタブ）。
const NOMINAL_LEG_DISTANCE_KM: i64 = 5_000;
/// 引取済とみなす予約状態（US21・料金算出の前提）。
const DELIVERED_STATUS: &str = "DELIVERED";

/// 既存 Booking/Handling/Routing から輸送実績を集約する ACL アダプター。
pub struct SqlxBookingActualsProvider {
    pool: PgPool,
}

impl SqlxBookingActualsProvider {
    /// アダプターを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn backend<E: std::fmt::Display>(e: E) -> BillingServiceError {
    BillingServiceError::Backend(e.to_string())
}

#[async_trait]
impl BookingActualsProvider for SqlxBookingActualsProvider {
    async fn find_actuals(
        &self,
        booking_id: &str,
    ) -> Result<Option<TransportActuals>, BillingServiceError> {
        let Some(row) = sqlx::query(
            r"SELECT shipper_id::text AS shipper_id, cargo_type, weight, booking_status
              FROM cargo WHERE booking_id = $1",
        )
        .bind(booking_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(backend)?
        else {
            return Ok(None);
        };

        let shipper_id: String = row.try_get("shipper_id").map_err(backend)?;
        let cargo_type: String = row.try_get("cargo_type").map_err(backend)?;
        let weight_kg: Decimal = row.try_get("weight").map_err(backend)?;
        let booking_status: String = row.try_get("booking_status").map_err(backend)?;

        // 選択経路のレグ数から名目輸送距離を導出する（distance カラム非保持のため暫定）。
        let leg_count: i64 = sqlx::query_scalar(
            r"SELECT COUNT(*) FROM selected_route_leg l
              JOIN selected_route r ON r.id = l.selected_route_id
              WHERE r.booking_id = $1",
        )
        .bind(booking_id)
        .fetch_one(&self.pool)
        .await
        .map_err(backend)?;
        let legs = leg_count.max(1);
        let distance_km = Decimal::from(legs * NOMINAL_LEG_DISTANCE_KM);

        Ok(Some(TransportActuals {
            shipper_id,
            cargo_type,
            weight_kg,
            distance_km,
            is_delivered: booking_status == DELIVERED_STATUS,
        }))
    }
}

/// Shipper の契約割引率を参照する ACL アダプター。個人荷主・未契約は 0。
pub struct SqlxShipperDiscountProvider {
    pool: PgPool,
}

impl SqlxShipperDiscountProvider {
    /// アダプターを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl ShipperDiscountProvider for SqlxShipperDiscountProvider {
    async fn find_discount_rate(&self, shipper_id: &str) -> Result<Decimal, BillingServiceError> {
        let row = sqlx::query(
            r"SELECT shipper_type, COALESCE(discount_rate, 0) AS discount_rate
              FROM shipper WHERE id = $1::uuid",
        )
        .bind(shipper_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(backend)?;
        let Some(row) = row else {
            return Ok(Decimal::ZERO);
        };
        let shipper_type: String = row.try_get("shipper_type").map_err(backend)?;
        // 個人荷主は割引対象外（US22）。
        if shipper_type != "CORPORATE" {
            return Ok(Decimal::ZERO);
        }
        let rate: Decimal = row.try_get("discount_rate").map_err(backend)?;
        Ok(rate)
    }
}

/// 精算通知 ACL（US23・送信＝記録）: notification テーブルへ記録する。
pub struct SqlxInvoiceNotificationPort {
    pool: PgPool,
}

impl SqlxInvoiceNotificationPort {
    /// アダプターを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

async fn record_billing_notification(
    pool: &PgPool,
    booking_id: &str,
    notification_type: &str,
    recipient_role: &str,
    recipient_email: &str,
    subject: &str,
    body: &str,
) -> Result<(), BillingServiceError> {
    sqlx::query(
        r"INSERT INTO notification
            (booking_id, notification_type, recipient_role, recipient_email, subject, body)
          VALUES ($1, $2, $3, $4, $5, $6)",
    )
    .bind(booking_id)
    .bind(notification_type)
    .bind(recipient_role)
    .bind(recipient_email)
    .bind(subject)
    .bind(body)
    .execute(pool)
    .await
    .map(|_| ())
    .map_err(backend)
}

async fn resolve_consignee(pool: &PgPool, booking_id: &str) -> String {
    sqlx::query_scalar::<_, String>("SELECT consignee_email FROM cargo WHERE booking_id = $1")
        .bind(booking_id)
        .fetch_optional(pool)
        .await
        .ok()
        .flatten()
        .filter(|c| !c.trim().is_empty())
        .unwrap_or_else(|| "ops@cargotracker.example".to_string())
}

/// 経理担当者への通知宛先（未払い通知・US23）。
const ACCOUNTING_RECIPIENT: &str = "accounting@cargotracker.example";

#[async_trait]
impl InvoiceNotificationPort for SqlxInvoiceNotificationPort {
    async fn notify_invoice_issued(
        &self,
        booking_id: &str,
        invoice_number: &str,
        amount: Decimal,
    ) -> Result<(), BillingServiceError> {
        let recipient = resolve_consignee(&self.pool, booking_id).await;
        record_billing_notification(
            &self.pool,
            booking_id,
            "INVOICE_ISSUED",
            "ROLE_SHIPPER",
            &recipient,
            "精算書を発行しました",
            &format!("精算書 {invoice_number}（請求金額 {amount} 円）を発行しました。"),
        )
        .await
    }

    async fn notify_payment_overdue(
        &self,
        booking_id: &str,
        invoice_number: &str,
    ) -> Result<(), BillingServiceError> {
        // 経理担当者（ROLE_BILLING）へ未払い通知（US23）。
        record_billing_notification(
            &self.pool,
            booking_id,
            "PAYMENT_OVERDUE",
            "ROLE_BILLING",
            ACCOUNTING_RECIPIENT,
            "支払期限を超過しています",
            &format!("精算書 {invoice_number} の支払期限が超過しました。対応してください。"),
        )
        .await
    }

    async fn notify_settlement_completed(
        &self,
        booking_id: &str,
        invoice_number: &str,
    ) -> Result<(), BillingServiceError> {
        // 精算完了を荷主（荷受人）へ通知する（US23）。
        let recipient = resolve_consignee(&self.pool, booking_id).await;
        record_billing_notification(
            &self.pool,
            booking_id,
            "SETTLEMENT_COMPLETED",
            "ROLE_SHIPPER",
            &recipient,
            "精算が完了しました",
            &format!("精算書 {invoice_number} の入金を確認し精算が完了しました。"),
        )
        .await
    }
}

/// 予約精算連携 ACL（US23）: Booking の `Cargo::settle()` を呼び予約を精算済にする（BC 独立）。
pub struct CargoBookingSettlement {
    cargo_repo: Arc<dyn CargoRepository>,
}

impl CargoBookingSettlement {
    /// アダプターを生成する。
    #[must_use]
    pub fn new(cargo_repo: Arc<dyn CargoRepository>) -> Self {
        Self { cargo_repo }
    }
}

#[async_trait]
impl BookingSettlementPort for CargoBookingSettlement {
    async fn settle(&self, booking_id: &str) -> Result<(), BillingServiceError> {
        let id = BookingId::parse(booking_id)
            .map_err(|_| BillingServiceError::NotFound(booking_id.to_string()))?;
        let mut cargo = self
            .cargo_repo
            .find_by_booking_id(&id)
            .await
            .map_err(backend)?
            .ok_or_else(|| BillingServiceError::NotFound(booking_id.to_string()))?;
        cargo
            .settle()
            .map_err(|e| BillingServiceError::Backend(e.to_string()))?;
        self.cargo_repo.save(&cargo).await.map_err(backend)?;
        Ok(())
    }
}

/// 決済機関連携 ACL（US23・スタブ）: 入金確認を常に成功として記録する。
///
/// 実際の外部決済機関連携（reqwest）は `infra-external` の契約テスト対象とし、composition の
/// 既定では送信＝記録の方針に沿って決定的に確認済みを返す（現行の通知スタブ方針と同一）。
pub struct StubPaymentGateway;

#[async_trait]
impl PaymentGatewayPort for StubPaymentGateway {
    async fn confirm_payment(
        &self,
        invoice_number: &str,
        _amount: Decimal,
    ) -> Result<PaymentConfirmation, BillingServiceError> {
        Ok(PaymentConfirmation {
            transaction_reference: format!("TXN-{invoice_number}"),
            payment_method: "BANK_TRANSFER".to_string(),
        })
    }
}
