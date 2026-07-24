//! Billing Context の出力ポート trait。

use crate::aggregate::FreightCharge;
use crate::invoice::Invoice;
use crate::value_objects::{BillingBookingId, FreightChargeId};

/// 永続化エラー。
#[derive(Debug, thiserror::Error)]
pub enum BillingRepositoryError {
    /// 永続化層のエラー。
    #[error("輸送料金の永続化に失敗しました: {0}")]
    Storage(String),
}

/// 輸送料金リポジトリ（出力ポート）。
#[async_trait::async_trait]
pub trait FreightChargeRepository: Send + Sync {
    /// 輸送料金を保存する（新規・更新兼用）。
    async fn save(&self, charge: &FreightCharge) -> Result<(), BillingRepositoryError>;

    /// 料金 ID で検索する。
    async fn find_by_id(
        &self,
        id: &FreightChargeId,
    ) -> Result<Option<FreightCharge>, BillingRepositoryError>;

    /// 予約 ID で検索する（二重算出防止・冪等）。
    async fn find_by_booking_id(
        &self,
        booking_id: &BillingBookingId,
    ) -> Result<Option<FreightCharge>, BillingRepositoryError>;

    /// 全料金を新しい順に取得する（一覧・Read Model）。
    async fn find_all(&self) -> Result<Vec<FreightCharge>, BillingRepositoryError>;
}

/// `Arc<dyn FreightChargeRepository>` へ委譲するブランケット実装（ADR-0003）。
#[async_trait::async_trait]
impl FreightChargeRepository for std::sync::Arc<dyn FreightChargeRepository> {
    async fn save(&self, charge: &FreightCharge) -> Result<(), BillingRepositoryError> {
        (**self).save(charge).await
    }
    async fn find_by_id(
        &self,
        id: &FreightChargeId,
    ) -> Result<Option<FreightCharge>, BillingRepositoryError> {
        (**self).find_by_id(id).await
    }
    async fn find_by_booking_id(
        &self,
        booking_id: &BillingBookingId,
    ) -> Result<Option<FreightCharge>, BillingRepositoryError> {
        (**self).find_by_booking_id(booking_id).await
    }
    async fn find_all(&self) -> Result<Vec<FreightCharge>, BillingRepositoryError> {
        (**self).find_all().await
    }
}

/// 精算書リポジトリ（出力ポート・US23）。
#[async_trait::async_trait]
pub trait InvoiceRepository: Send + Sync {
    /// 精算書を保存する（新規・更新兼用）。
    async fn save(&self, invoice: &Invoice) -> Result<(), BillingRepositoryError>;

    /// 精算書番号で検索する。
    async fn find_by_number(
        &self,
        invoice_number: &str,
    ) -> Result<Option<Invoice>, BillingRepositoryError>;

    /// 予約 ID で検索する（二重発行防止・冪等）。
    async fn find_by_booking_id(
        &self,
        booking_id: &BillingBookingId,
    ) -> Result<Option<Invoice>, BillingRepositoryError>;

    /// 全精算書を新しい順に取得する（一覧・Read Model）。
    async fn find_all(&self) -> Result<Vec<Invoice>, BillingRepositoryError>;
}

/// `Arc<dyn InvoiceRepository>` へ委譲するブランケット実装（ADR-0003）。
#[async_trait::async_trait]
impl InvoiceRepository for std::sync::Arc<dyn InvoiceRepository> {
    async fn save(&self, invoice: &Invoice) -> Result<(), BillingRepositoryError> {
        (**self).save(invoice).await
    }
    async fn find_by_number(
        &self,
        invoice_number: &str,
    ) -> Result<Option<Invoice>, BillingRepositoryError> {
        (**self).find_by_number(invoice_number).await
    }
    async fn find_by_booking_id(
        &self,
        booking_id: &BillingBookingId,
    ) -> Result<Option<Invoice>, BillingRepositoryError> {
        (**self).find_by_booking_id(booking_id).await
    }
    async fn find_all(&self) -> Result<Vec<Invoice>, BillingRepositoryError> {
        (**self).find_all().await
    }
}
