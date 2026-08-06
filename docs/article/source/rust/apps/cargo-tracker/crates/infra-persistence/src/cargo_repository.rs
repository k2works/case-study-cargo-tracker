//! `CargoRepository` と `ShipperExistenceChecker` の sqlx 実装。

use async_trait::async_trait;
use chrono::NaiveDate;
use domain_booking::{
    AclError, BookingId, BookingStatus, Cargo, CargoRepository, CargoType, Consignee, Description,
    Dimensions, HazardousDeclaration, Quantity, RepositoryError, RouteSpecification,
    ShipperExistenceChecker, TemperatureRequirement, TemperatureUnit, Weight,
};
use rust_decimal::Decimal;
use shared_kernel::{Location, ShipperId};
use sqlx::PgPool;
use sqlx::Row;

/// PostgreSQL による貨物リポジトリ実装。
pub struct SqlxCargoRepository {
    pool: PgPool,
}

impl SqlxCargoRepository {
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
impl CargoRepository for SqlxCargoRepository {
    async fn save(&self, cargo: &Cargo) -> Result<(), RepositoryError> {
        let dims = cargo.dimensions();
        let (haz_class, un_number, psn) = match cargo.hazardous_declaration() {
            Some(h) => (
                Some(h.hazardous_class().to_string()),
                Some(h.un_number().to_string()),
                Some(h.proper_shipping_name().to_string()),
            ),
            None => (None, None, None),
        };
        let (min_temp, max_temp, temp_unit) = match cargo.temperature_requirement() {
            Some(t) => (
                Some(t.min_temperature()),
                Some(t.max_temperature()),
                Some(t.unit().as_str().to_string()),
            ),
            None => (None, None, None),
        };

        sqlx::query(
            r"INSERT INTO cargo
                (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
                 arrival_deadline, consignee_name, consignee_email, booking_status,
                 dimension_length, dimension_width, dimension_height, quantity, description,
                 hazardous_class, un_number, proper_shipping_name,
                 min_temperature, max_temperature, temperature_unit)
              VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21)
              ON CONFLICT (booking_id) DO UPDATE SET
                booking_status = EXCLUDED.booking_status,
                updated_at = NOW()",
        )
        .bind(cargo.booking_id().as_str())
        .bind(cargo.shipper_id().value())
        .bind(cargo.cargo_type().as_str())
        .bind(cargo.weight().value())
        .bind(cargo.route_specification().origin().code())
        .bind(cargo.route_specification().destination().code())
        .bind(cargo.route_specification().arrival_deadline())
        .bind(cargo.consignee().name())
        .bind(cargo.consignee().contact())
        .bind(cargo.status().as_str())
        .bind(dims.map(|d| d.length))
        .bind(dims.map(|d| d.width))
        .bind(dims.map(|d| d.height))
        .bind(
            cargo
                .quantity()
                .map(|q| i32::try_from(q.value()).unwrap_or(i32::MAX)),
        )
        .bind(cargo.description().map(|d| d.as_str().to_string()))
        .bind(haz_class)
        .bind(un_number)
        .bind(psn)
        .bind(min_temp)
        .bind(max_temp)
        .bind(temp_unit)
        .execute(&self.pool)
        .await
        .map_err(backend)?;
        Ok(())
    }

    async fn find_by_booking_id(&self, id: &BookingId) -> Result<Option<Cargo>, RepositoryError> {
        let row = sqlx::query(r"SELECT * FROM cargo WHERE booking_id = $1")
            .bind(id.as_str())
            .fetch_optional(&self.pool)
            .await
            .map_err(backend)?;
        match row {
            Some(row) => Ok(Some(row_to_cargo(&row)?)),
            None => Ok(None),
        }
    }

    async fn find_all(&self) -> Result<Vec<Cargo>, RepositoryError> {
        let rows = sqlx::query(r"SELECT * FROM cargo ORDER BY booking_id")
            .fetch_all(&self.pool)
            .await
            .map_err(backend)?;
        rows.iter().map(row_to_cargo).collect()
    }
}

/// `cargo` テーブルの 1 行を貨物集約へ復元する。
fn row_to_cargo(row: &sqlx::postgres::PgRow) -> Result<Cargo, RepositoryError> {
    {
        let origin = Location::new(
            &row.try_get::<String, _>("origin_unlocode")
                .map_err(backend)?,
        )
        .map_err(backend)?;
        let destination = Location::new(
            &row.try_get::<String, _>("destination_unlocode")
                .map_err(backend)?,
        )
        .map_err(backend)?;
        let deadline: NaiveDate = row.try_get("arrival_deadline").map_err(backend)?;
        let route = RouteSpecification::new(origin, destination, deadline).map_err(backend)?;

        let consignee = Consignee::new(
            row.try_get::<String, _>("consignee_name")
                .map_err(backend)?,
            row.try_get::<String, _>("consignee_email")
                .map_err(backend)?,
        )
        .map_err(backend)?;

        let cargo_type = CargoType::from_str_or_general(
            &row.try_get::<String, _>("cargo_type").map_err(backend)?,
        );
        let status = BookingStatus::from_str_or_preliminary(
            &row.try_get::<String, _>("booking_status")
                .map_err(backend)?,
        );
        let weight =
            Weight::new(row.try_get::<Decimal, _>("weight").map_err(backend)?).map_err(backend)?;

        let dimensions = match (
            row.try_get::<Option<Decimal>, _>("dimension_length")
                .map_err(backend)?,
            row.try_get::<Option<Decimal>, _>("dimension_width")
                .map_err(backend)?,
            row.try_get::<Option<Decimal>, _>("dimension_height")
                .map_err(backend)?,
        ) {
            (Some(length), Some(width), Some(height)) => Some(Dimensions {
                length,
                width,
                height,
            }),
            _ => None,
        };

        let quantity = row
            .try_get::<Option<i32>, _>("quantity")
            .map_err(backend)?
            .map(|q| Quantity::new(u32::try_from(q).unwrap_or(0)))
            .transpose()
            .map_err(backend)?;

        let description = row
            .try_get::<Option<String>, _>("description")
            .map_err(backend)?
            .map(Description::new)
            .transpose()
            .map_err(backend)?;

        let hazardous_declaration = match (
            row.try_get::<Option<String>, _>("hazardous_class")
                .map_err(backend)?,
            row.try_get::<Option<String>, _>("un_number")
                .map_err(backend)?,
            row.try_get::<Option<String>, _>("proper_shipping_name")
                .map_err(backend)?,
        ) {
            (Some(class), Some(un), Some(psn)) => {
                Some(HazardousDeclaration::new(class, un, psn).map_err(backend)?)
            }
            _ => None,
        };

        let temperature_requirement = match (
            row.try_get::<Option<Decimal>, _>("min_temperature")
                .map_err(backend)?,
            row.try_get::<Option<Decimal>, _>("max_temperature")
                .map_err(backend)?,
            row.try_get::<Option<String>, _>("temperature_unit")
                .map_err(backend)?,
        ) {
            (Some(min), Some(max), Some(unit)) => Some(
                TemperatureRequirement::new(min, max, TemperatureUnit::from_str_or_celsius(&unit))
                    .map_err(backend)?,
            ),
            _ => None,
        };

        let cargo = Cargo::reconstitute(
            BookingId::parse(row.try_get::<String, _>("booking_id").map_err(backend)?)
                .map_err(backend)?,
            ShipperId::from_uuid(row.try_get("shipper_id").map_err(backend)?),
            route,
            consignee,
            cargo_type,
            weight,
            status,
            dimensions,
            quantity,
            description,
            hazardous_declaration,
            temperature_requirement,
        );
        Ok(cargo)
    }
}

/// shipper テーブルを参照する in-process ACL 実装（Booking → Shipper の存在確認）。
pub struct SqlxShipperExistenceChecker {
    pool: PgPool,
}

impl SqlxShipperExistenceChecker {
    /// コネクションプールから ACL を生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl ShipperExistenceChecker for SqlxShipperExistenceChecker {
    async fn exists(&self, shipper_id: &ShipperId) -> Result<bool, AclError> {
        let row = sqlx::query(r"SELECT EXISTS(SELECT 1 FROM shipper WHERE id = $1) AS present")
            .bind(shipper_id.value())
            .fetch_one(&self.pool)
            .await
            .map_err(|e| AclError::Backend(e.to_string()))?;
        row.try_get("present")
            .map_err(|e| AclError::Backend(e.to_string()))
    }
}
