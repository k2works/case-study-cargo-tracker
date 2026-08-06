//! `InvoiceRepository` の sqlx 実装（US23）。
//!
//! `invoice`（集約ルート）と `invoice_line_item`（明細）・`payment`（支払記録）をトランザクションで
//! 保存し、精算書番号・予約 ID・一覧で再構築する。予約 1 件に 1 精算書（UNIQUE・冪等）。

use async_trait::async_trait;
use domain_billing::{
    BillingBookingId, BillingRepositoryError, Invoice, InvoiceId, InvoiceLineItem,
    InvoiceRepository, Money, Payment, PaymentMethod, PaymentStatus,
};
use rust_decimal::Decimal;
use sqlx::{PgPool, Row};

/// PostgreSQL による精算書リポジトリ実装。
pub struct SqlxInvoiceRepository {
    pool: PgPool,
}

impl SqlxInvoiceRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn backend<E: std::fmt::Display>(e: E) -> BillingRepositoryError {
    BillingRepositoryError::Storage(e.to_string())
}

const INVOICE_COLUMNS: &str = "id, invoice_number, booking_id, charge_total_value, tax_rate, \
     tax_amount, total_amount_value, payment_status, issued_at, due_date";

impl SqlxInvoiceRepository {
    async fn reconstruct(
        &self,
        row: &sqlx::postgres::PgRow,
    ) -> Result<Invoice, BillingRepositoryError> {
        let id: i64 = row.try_get("id").map_err(backend)?;
        let invoice_number: String = row.try_get("invoice_number").map_err(backend)?;
        let booking_id: String = row.try_get("booking_id").map_err(backend)?;
        let charge_total: Decimal = row.try_get("charge_total_value").map_err(backend)?;
        let tax_rate: Decimal = row.try_get("tax_rate").map_err(backend)?;
        let tax_amount: Decimal = row.try_get("tax_amount").map_err(backend)?;
        let total_amount: Decimal = row.try_get("total_amount_value").map_err(backend)?;
        let payment_status: String = row.try_get("payment_status").map_err(backend)?;
        let issued_at: Option<chrono::DateTime<chrono::Utc>> =
            row.try_get("issued_at").map_err(backend)?;
        let due_date: chrono::NaiveDate = row.try_get("due_date").map_err(backend)?;

        let line_rows = sqlx::query(
            r"SELECT description, amount_value, seq_number FROM invoice_line_item
              WHERE invoice_id = $1 ORDER BY seq_number ASC",
        )
        .bind(id)
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;
        let line_items = line_rows
            .iter()
            .map(|r| {
                let description: String = r.try_get("description").map_err(backend)?;
                let amount: Decimal = r.try_get("amount_value").map_err(backend)?;
                let seq: i32 = r.try_get("seq_number").map_err(backend)?;
                Ok(InvoiceLineItem::new(description, Money::jpy(amount), seq))
            })
            .collect::<Result<Vec<_>, BillingRepositoryError>>()?;

        let payment_rows = sqlx::query(
            r"SELECT paid_amount_value, paid_at, payment_method, transaction_reference
              FROM payment WHERE invoice_id = $1 ORDER BY id ASC",
        )
        .bind(id)
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;
        let payments = payment_rows
            .iter()
            .map(|r| {
                let amount: Decimal = r.try_get("paid_amount_value").map_err(backend)?;
                let paid_at: chrono::DateTime<chrono::Utc> =
                    r.try_get("paid_at").map_err(backend)?;
                let method: String = r.try_get("payment_method").map_err(backend)?;
                let txn: Option<String> = r.try_get("transaction_reference").map_err(backend)?;
                Ok(Payment::new(
                    Money::jpy(amount),
                    paid_at,
                    PaymentMethod::from_str_or_bank_transfer(&method),
                    txn,
                ))
            })
            .collect::<Result<Vec<_>, BillingRepositoryError>>()?;

        Ok(Invoice::reconstruct(
            InvoiceId::generate(),
            invoice_number,
            BillingBookingId::parse(booking_id).map_err(backend)?,
            Money::jpy(charge_total),
            tax_rate,
            Money::jpy(tax_amount),
            Money::jpy(total_amount),
            PaymentStatus::from_str_or_pending(&payment_status),
            issued_at,
            due_date,
            line_items,
            payments,
        ))
    }
}

#[async_trait]
impl InvoiceRepository for SqlxInvoiceRepository {
    async fn save(&self, invoice: &Invoice) -> Result<(), BillingRepositoryError> {
        let mut tx = self.pool.begin().await.map_err(backend)?;

        let invoice_pk: i64 = sqlx::query(
            r"INSERT INTO invoice
                (invoice_number, booking_id, charge_total_value, charge_total_currency,
                 tax_rate, tax_amount, total_amount_value, total_amount_currency,
                 payment_status, issued_at, due_date)
              VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
              ON CONFLICT (booking_id)
              DO UPDATE SET payment_status = EXCLUDED.payment_status, updated_at = NOW()
              RETURNING id",
        )
        .bind(invoice.invoice_number())
        .bind(invoice.booking_id().as_str())
        .bind(invoice.charge_total().amount())
        .bind(invoice.charge_total().currency().as_str())
        .bind(invoice.tax_rate())
        .bind(invoice.tax_amount().amount())
        .bind(invoice.total_amount().amount())
        .bind(invoice.total_amount().currency().as_str())
        .bind(invoice.payment_status().as_str())
        .bind(invoice.issued_at())
        .bind(invoice.due_date())
        .fetch_one(&mut *tx)
        .await
        .map_err(backend)?
        .try_get("id")
        .map_err(backend)?;

        // 明細は洗い替え。
        sqlx::query(r"DELETE FROM invoice_line_item WHERE invoice_id = $1")
            .bind(invoice_pk)
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        for item in invoice.line_items() {
            sqlx::query(
                r"INSERT INTO invoice_line_item
                    (invoice_id, description, amount_value, amount_currency, seq_number)
                  VALUES ($1, $2, $3, $4, $5)",
            )
            .bind(invoice_pk)
            .bind(item.description())
            .bind(item.amount().amount())
            .bind(item.amount().currency().as_str())
            .bind(item.seq_number())
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        }

        // 支払記録は洗い替え。
        sqlx::query(r"DELETE FROM payment WHERE invoice_id = $1")
            .bind(invoice_pk)
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        for p in invoice.payments() {
            sqlx::query(
                r"INSERT INTO payment
                    (invoice_id, paid_amount_value, paid_amount_currency, paid_at,
                     payment_method, transaction_reference)
                  VALUES ($1, $2, $3, $4, $5, $6)",
            )
            .bind(invoice_pk)
            .bind(p.paid_amount().amount())
            .bind(p.paid_amount().currency().as_str())
            .bind(p.paid_at())
            .bind(p.payment_method().as_str())
            .bind(p.transaction_reference())
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        }

        tx.commit().await.map_err(backend)?;
        Ok(())
    }

    async fn find_by_number(
        &self,
        invoice_number: &str,
    ) -> Result<Option<Invoice>, BillingRepositoryError> {
        let sql = format!("SELECT {INVOICE_COLUMNS} FROM invoice WHERE invoice_number = $1");
        let row = sqlx::query(&sql)
            .bind(invoice_number)
            .fetch_optional(&self.pool)
            .await
            .map_err(backend)?;
        match row {
            Some(r) => Ok(Some(self.reconstruct(&r).await?)),
            None => Ok(None),
        }
    }

    async fn find_by_booking_id(
        &self,
        booking_id: &BillingBookingId,
    ) -> Result<Option<Invoice>, BillingRepositoryError> {
        let sql = format!("SELECT {INVOICE_COLUMNS} FROM invoice WHERE booking_id = $1");
        let row = sqlx::query(&sql)
            .bind(booking_id.as_str())
            .fetch_optional(&self.pool)
            .await
            .map_err(backend)?;
        match row {
            Some(r) => Ok(Some(self.reconstruct(&r).await?)),
            None => Ok(None),
        }
    }

    async fn find_all(&self) -> Result<Vec<Invoice>, BillingRepositoryError> {
        let sql =
            format!("SELECT {INVOICE_COLUMNS} FROM invoice ORDER BY created_at DESC, id DESC");
        let rows = sqlx::query(&sql)
            .fetch_all(&self.pool)
            .await
            .map_err(backend)?;
        let mut invoices = Vec::with_capacity(rows.len());
        for r in &rows {
            invoices.push(self.reconstruct(r).await?);
        }
        Ok(invoices)
    }
}
