//! `ShipperRepository` の sqlx 実装。

use async_trait::async_trait;
use domain_shipper::{
    Address, ContractNumber, CorporateProfile, DiscountRate, Email, Phone, RepositoryError,
    Shipper, ShipperCode, ShipperKind, ShipperName, ShipperRepository,
};
use rust_decimal::Decimal;
use shared_kernel::ShipperId;
use sqlx::PgPool;
use sqlx::Row;

/// PostgreSQL による荷主リポジトリ実装。
pub struct SqlxShipperRepository {
    pool: PgPool,
}

impl SqlxShipperRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

fn backend<E: std::fmt::Display>(e: E) -> RepositoryError {
    RepositoryError::Backend(e.to_string())
}

#[async_trait]
impl ShipperRepository for SqlxShipperRepository {
    async fn save(&self, shipper: &Shipper) -> Result<(), RepositoryError> {
        let (shipper_type, contract_number, discount_rate) = match shipper.kind() {
            ShipperKind::Individual => ("INDIVIDUAL", None, Decimal::ZERO),
            ShipperKind::Corporate(profile) => (
                "CORPORATE",
                Some(profile.contract_number().as_str().to_string()),
                profile.discount_rate().value(),
            ),
        };

        sqlx::query(
            r"INSERT INTO shipper
                (id, shipper_code, shipper_type, name, email, phone, address, contract_number, discount_rate)
              VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)",
        )
        .bind(shipper.id().value())
        .bind(shipper.code().as_str())
        .bind(shipper_type)
        .bind(shipper.name().as_str())
        .bind(shipper.email().as_str())
        .bind(shipper.phone().map(|p| p.as_str().to_string()))
        .bind(shipper.address().map(|a| a.as_str().to_string()))
        .bind(contract_number)
        .bind(discount_rate)
        .execute(&self.pool)
        .await
        .map_err(backend)?;
        Ok(())
    }

    async fn find_by_id(&self, id: &ShipperId) -> Result<Option<Shipper>, RepositoryError> {
        let row = sqlx::query(
            r"SELECT id, shipper_code, shipper_type, name, email, phone, address,
                     contract_number, discount_rate
              FROM shipper WHERE id = $1",
        )
        .bind(id.value())
        .fetch_optional(&self.pool)
        .await
        .map_err(backend)?;

        let Some(row) = row else {
            return Ok(None);
        };

        let shipper_type: String = row.try_get("shipper_type").map_err(backend)?;
        let kind = match shipper_type.as_str() {
            "CORPORATE" => {
                let contract: String = row.try_get("contract_number").map_err(backend)?;
                let rate: Decimal = row.try_get("discount_rate").map_err(backend)?;
                let profile = CorporateProfile::new(
                    ContractNumber::new(contract).map_err(backend)?,
                    DiscountRate::new(rate).map_err(backend)?,
                );
                ShipperKind::Corporate(profile)
            }
            _ => ShipperKind::Individual,
        };

        let phone: Option<String> = row.try_get("phone").map_err(backend)?;
        let address: Option<String> = row.try_get("address").map_err(backend)?;

        let shipper = Shipper::reconstitute(
            ShipperId::from_uuid(row.try_get("id").map_err(backend)?),
            ShipperCode::from_string(row.try_get("shipper_code").map_err(backend)?),
            ShipperName::new(row.try_get::<String, _>("name").map_err(backend)?)
                .map_err(backend)?,
            Email::new(row.try_get::<String, _>("email").map_err(backend)?).map_err(backend)?,
            phone.map(Phone::new).transpose().map_err(backend)?,
            address.map(Address::new).transpose().map_err(backend)?,
            kind,
        );
        Ok(Some(shipper))
    }

    async fn exists_by_email(&self, email: &Email) -> Result<bool, RepositoryError> {
        let row = sqlx::query(r"SELECT EXISTS(SELECT 1 FROM shipper WHERE email = $1) AS present")
            .bind(email.as_str())
            .fetch_one(&self.pool)
            .await
            .map_err(backend)?;
        let present: bool = row.try_get("present").map_err(backend)?;
        Ok(present)
    }
}
