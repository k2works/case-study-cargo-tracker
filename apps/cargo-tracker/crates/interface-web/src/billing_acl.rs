//! Billing Context の ACL アダプター（composition 層）。
//!
//! `app-billing` の `BookingActualsProvider`／`ShipperDiscountProvider` を既存の Booking/Handling/
//! Routing・Shipper へ橋渡しする。ドメインクレート同士は依存せず、結線をここに閉じ込める（BC 独立）。
//! 輸送距離は経路（選択レグ数）ベースの暫定導出（distance カラムを持たないため。将来差し替え）。

use app_billing::{
    BillingServiceError, BookingActualsProvider, ShipperDiscountProvider, TransportActuals,
};
use async_trait::async_trait;
use rust_decimal::Decimal;
use sqlx::{PgPool, Row};

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
