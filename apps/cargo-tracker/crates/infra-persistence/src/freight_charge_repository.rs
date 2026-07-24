//! `FreightChargeRepository` の sqlx 実装（US21/US22）。
//!
//! `freight_charge`（集約ルート）と `freight_charge_adjustment`（料金調整）をトランザクションで
//! 保存し、料金 ID・予約 ID・一覧で再構築する。予約 1 件に 1 料金（UNIQUE・冪等）。

use async_trait::async_trait;
use domain_billing::{
    AdjustmentReason, BillingBookingId, BillingRepositoryError, ChargeAdjustment, ChargeStatus,
    DiscountLine, DiscountRate, FreightCharge, FreightChargeId, FreightChargeRepository, Money,
};
use rust_decimal::Decimal;
use sqlx::{PgPool, Row};

/// PostgreSQL による輸送料金リポジトリ実装。
pub struct SqlxFreightChargeRepository {
    pool: PgPool,
}

impl SqlxFreightChargeRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn backend<E: std::fmt::Display>(e: E) -> BillingRepositoryError {
    BillingRepositoryError::Storage(e.to_string())
}

impl SqlxFreightChargeRepository {
    async fn reconstruct(
        &self,
        row: &sqlx::postgres::PgRow,
    ) -> Result<FreightCharge, BillingRepositoryError> {
        let id: i64 = row.try_get("id").map_err(backend)?;
        let charge_id: String = row.try_get("charge_id").map_err(backend)?;
        let booking_id: String = row.try_get("booking_id").map_err(backend)?;
        let base_value: Decimal = row.try_get("base_amount_value").map_err(backend)?;
        let discount_rate: Option<Decimal> = row.try_get("discount_rate").map_err(backend)?;
        let discount_value: Option<Decimal> =
            row.try_get("discount_amount_value").map_err(backend)?;
        let status: String = row.try_get("status").map_err(backend)?;

        let base_amount = Money::jpy(base_value);
        let discount = match (discount_rate, discount_value) {
            (Some(rate), Some(amount)) => {
                let rate = DiscountRate::new(rate).map_err(backend)?;
                Some(DiscountLine::reconstruct(
                    rate,
                    base_amount,
                    Money::jpy(amount),
                ))
            }
            _ => None,
        };

        let adjustment_rows = sqlx::query(
            r"SELECT reason, amount_value FROM freight_charge_adjustment
              WHERE freight_charge_id = $1 ORDER BY id ASC",
        )
        .bind(id)
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;
        let mut adjustments = Vec::with_capacity(adjustment_rows.len());
        for r in &adjustment_rows {
            let reason: String = r.try_get("reason").map_err(backend)?;
            let amount: Decimal = r.try_get("amount_value").map_err(backend)?;
            let reason = AdjustmentReason::parse(&reason)
                .ok_or_else(|| backend(format!("未知の調整理由: {reason}")))?;
            adjustments.push(ChargeAdjustment::new(reason, Money::jpy(amount)));
        }

        Ok(FreightCharge::reconstruct(
            FreightChargeId::from_string(charge_id),
            BillingBookingId::parse(booking_id).map_err(backend)?,
            base_amount,
            adjustments,
            discount,
            ChargeStatus::from_str_or_draft(&status),
        ))
    }
}

#[async_trait]
impl FreightChargeRepository for SqlxFreightChargeRepository {
    async fn save(&self, charge: &FreightCharge) -> Result<(), BillingRepositoryError> {
        let mut tx = self.pool.begin().await.map_err(backend)?;

        let total = charge.total().map_err(backend)?;
        let (discount_rate, discount_value) = charge.discount().map_or((None, None), |d| {
            (Some(d.rate().value()), Some(d.discount_amount().amount()))
        });
        let confirmed = matches!(charge.status(), ChargeStatus::Confirmed);

        let charge_pk: i64 = sqlx::query(
            r"INSERT INTO freight_charge
                (charge_id, booking_id, base_amount_value, base_amount_currency,
                 discount_rate, discount_amount_value, total_amount_value, total_amount_currency,
                 status, confirmed_at)
              VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9,
                      CASE WHEN $10 THEN NOW() ELSE NULL END)
              ON CONFLICT (booking_id)
              DO UPDATE SET base_amount_value = EXCLUDED.base_amount_value,
                            discount_rate = EXCLUDED.discount_rate,
                            discount_amount_value = EXCLUDED.discount_amount_value,
                            total_amount_value = EXCLUDED.total_amount_value,
                            status = EXCLUDED.status,
                            confirmed_at = CASE WHEN $10 THEN NOW()
                                                ELSE freight_charge.confirmed_at END,
                            updated_at = NOW()
              RETURNING id",
        )
        .bind(charge.charge_id().as_str())
        .bind(charge.booking_id().as_str())
        .bind(charge.base_amount().amount())
        .bind(charge.base_amount().currency().as_str())
        .bind(discount_rate)
        .bind(discount_value)
        .bind(total.amount())
        .bind(total.currency().as_str())
        .bind(charge.status().as_str())
        .bind(confirmed)
        .fetch_one(&mut *tx)
        .await
        .map_err(backend)?
        .try_get("id")
        .map_err(backend)?;

        // 調整は洗い替え。
        sqlx::query(r"DELETE FROM freight_charge_adjustment WHERE freight_charge_id = $1")
            .bind(charge_pk)
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        for adj in charge.adjustments() {
            sqlx::query(
                r"INSERT INTO freight_charge_adjustment
                    (freight_charge_id, reason, amount_value, amount_currency)
                  VALUES ($1, $2, $3, $4)",
            )
            .bind(charge_pk)
            .bind(adj.reason().as_str())
            .bind(adj.amount().amount())
            .bind(adj.amount().currency().as_str())
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        }

        tx.commit().await.map_err(backend)?;
        Ok(())
    }

    async fn find_by_id(
        &self,
        id: &FreightChargeId,
    ) -> Result<Option<FreightCharge>, BillingRepositoryError> {
        let row = sqlx::query(
            r"SELECT id, charge_id, booking_id, base_amount_value, discount_rate,
                     discount_amount_value, status
              FROM freight_charge WHERE charge_id = $1",
        )
        .bind(id.as_str())
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
    ) -> Result<Option<FreightCharge>, BillingRepositoryError> {
        let row = sqlx::query(
            r"SELECT id, charge_id, booking_id, base_amount_value, discount_rate,
                     discount_amount_value, status
              FROM freight_charge WHERE booking_id = $1",
        )
        .bind(booking_id.as_str())
        .fetch_optional(&self.pool)
        .await
        .map_err(backend)?;
        match row {
            Some(r) => Ok(Some(self.reconstruct(&r).await?)),
            None => Ok(None),
        }
    }

    async fn find_all(&self) -> Result<Vec<FreightCharge>, BillingRepositoryError> {
        let rows = sqlx::query(
            r"SELECT id, charge_id, booking_id, base_amount_value, discount_rate,
                     discount_amount_value, status
              FROM freight_charge ORDER BY created_at DESC, id DESC",
        )
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;
        let mut charges = Vec::with_capacity(rows.len());
        for r in &rows {
            charges.push(self.reconstruct(r).await?);
        }
        Ok(charges)
    }
}
