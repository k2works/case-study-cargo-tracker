//! `VoyageRepository` の sqlx 実装。

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use domain_routing::{
    CargoType, Carrier, CarrierMovement, RepositoryError, Schedule, VesselName, Voyage,
    VoyageNumber, VoyageRepository, VoyageSearchCriteria,
};
use shared_kernel::Location;
use sqlx::{PgPool, Row};

/// PostgreSQL による航海リポジトリ実装。
pub struct SqlxVoyageRepository {
    pool: PgPool,
}

impl SqlxVoyageRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// 単一 voyage 行から Voyage 集約を組み立てる。
    async fn load_voyage(&self, voyage_id: i64) -> Result<Voyage, RepositoryError> {
        let head =
            sqlx::query(r"SELECT voyage_number, vessel_name, carrier FROM voyage WHERE id = $1")
                .bind(voyage_id)
                .fetch_one(&self.pool)
                .await
                .map_err(backend)?;

        let movements = sqlx::query(
            r"SELECT departure_location_unlocode, arrival_location_unlocode,
                     departure_date, arrival_date
              FROM carrier_movement WHERE voyage_id = $1 ORDER BY seq_number",
        )
        .bind(voyage_id)
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;

        let mut legs = Vec::with_capacity(movements.len());
        for row in movements {
            let dep: String = row
                .try_get("departure_location_unlocode")
                .map_err(backend)?;
            let arr: String = row.try_get("arrival_location_unlocode").map_err(backend)?;
            let dep_time: DateTime<Utc> = row.try_get("departure_date").map_err(backend)?;
            let arr_time: DateTime<Utc> = row.try_get("arrival_date").map_err(backend)?;
            let movement = CarrierMovement::new(
                Location::new(&dep).map_err(backend)?,
                Location::new(&arr).map_err(backend)?,
                dep_time,
                arr_time,
            )
            .map_err(backend)?;
            legs.push(movement);
        }
        let schedule = Schedule::new(legs).map_err(backend)?;

        let cargo_rows =
            sqlx::query(r"SELECT cargo_type FROM voyage_cargo_type WHERE voyage_id = $1")
                .bind(voyage_id)
                .fetch_all(&self.pool)
                .await
                .map_err(backend)?;
        let mut cargo_types = Vec::with_capacity(cargo_rows.len());
        for row in cargo_rows {
            let value: String = row.try_get("cargo_type").map_err(backend)?;
            cargo_types.push(CargoType::parse(&value).map_err(backend)?);
        }

        Ok(Voyage::reconstitute(
            VoyageNumber::from_string(head.try_get("voyage_number").map_err(backend)?),
            VesselName::from_string(head.try_get("vessel_name").map_err(backend)?),
            Carrier::from_string(head.try_get("carrier").map_err(backend)?),
            cargo_types,
            schedule,
        ))
    }

    async fn find_id(&self, number: &VoyageNumber) -> Result<Option<i64>, RepositoryError> {
        let row = sqlx::query(r"SELECT id FROM voyage WHERE voyage_number = $1")
            .bind(number.as_str())
            .fetch_optional(&self.pool)
            .await
            .map_err(backend)?;
        row.map(|r| r.try_get::<i64, _>("id").map_err(backend))
            .transpose()
    }
}

fn backend<E: std::fmt::Display>(e: E) -> RepositoryError {
    RepositoryError::Backend(e.to_string())
}

#[async_trait]
impl VoyageRepository for SqlxVoyageRepository {
    async fn save(&self, voyage: &Voyage) -> Result<(), RepositoryError> {
        let mut tx = self.pool.begin().await.map_err(backend)?;

        // upsert voyage 本体（voyage_number を業務キーとする）
        let voyage_id: i64 = sqlx::query(
            r"INSERT INTO voyage (voyage_number, vessel_name, carrier)
              VALUES ($1, $2, $3)
              ON CONFLICT (voyage_number)
              DO UPDATE SET vessel_name = EXCLUDED.vessel_name,
                            carrier = EXCLUDED.carrier,
                            updated_at = NOW()
              RETURNING id",
        )
        .bind(voyage.voyage_number().as_str())
        .bind(voyage.vessel_name().as_str())
        .bind(voyage.carrier().as_str())
        .fetch_one(&mut *tx)
        .await
        .map_err(backend)?
        .try_get("id")
        .map_err(backend)?;

        // 子テーブルは全削除して再挿入する（スケジュール上書き更新に対応）
        sqlx::query(r"DELETE FROM carrier_movement WHERE voyage_id = $1")
            .bind(voyage_id)
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        sqlx::query(r"DELETE FROM voyage_cargo_type WHERE voyage_id = $1")
            .bind(voyage_id)
            .execute(&mut *tx)
            .await
            .map_err(backend)?;

        for (seq, movement) in voyage.schedule().carrier_movements().iter().enumerate() {
            sqlx::query(
                r"INSERT INTO carrier_movement
                    (voyage_id, departure_location_unlocode, arrival_location_unlocode,
                     departure_date, arrival_date, seq_number)
                  VALUES ($1, $2, $3, $4, $5, $6)",
            )
            .bind(voyage_id)
            .bind(movement.departure_location().code())
            .bind(movement.arrival_location().code())
            .bind(movement.departure_time())
            .bind(movement.arrival_time())
            .bind(i32::try_from(seq + 1).unwrap_or(i32::MAX))
            .execute(&mut *tx)
            .await
            .map_err(backend)?;
        }

        for cargo_type in voyage.supported_cargo_types() {
            sqlx::query(r"INSERT INTO voyage_cargo_type (voyage_id, cargo_type) VALUES ($1, $2)")
                .bind(voyage_id)
                .bind(cargo_type.as_str())
                .execute(&mut *tx)
                .await
                .map_err(backend)?;
        }

        tx.commit().await.map_err(backend)?;
        Ok(())
    }

    async fn find_by_voyage_number(
        &self,
        number: &VoyageNumber,
    ) -> Result<Option<Voyage>, RepositoryError> {
        match self.find_id(number).await? {
            Some(id) => Ok(Some(self.load_voyage(id).await?)),
            None => Ok(None),
        }
    }

    async fn exists(&self, number: &VoyageNumber) -> Result<bool, RepositoryError> {
        Ok(self.find_id(number).await?.is_some())
    }

    async fn search(
        &self,
        criteria: &VoyageSearchCriteria,
    ) -> Result<Vec<Voyage>, RepositoryError> {
        // 全件ロード後の Rust フィルタ（N+1）を避け、DB 側で voyage ID を絞り込む。
        // 出発地はスケジュール先頭（seq 最小）の carrier_movement の departure、
        // 到着地は末尾（seq 最大）の arrival に対応するため EXISTS 副問い合わせで表現する。
        // 各条件は「引数が NULL なら素通し」で任意化する（VoyageSearchCriteria の Option 意味論に一致）。
        let origin = criteria.origin.as_ref().map(|l| l.code().to_string());
        let destination = criteria.destination.as_ref().map(|l| l.code().to_string());
        let cargo_type = criteria.cargo_type.map(|c| c.as_str().to_string());

        let ids = sqlx::query(
            r"SELECT v.id FROM voyage v
              WHERE ($1::text IS NULL OR EXISTS (
                        SELECT 1 FROM voyage_cargo_type vct
                        WHERE vct.voyage_id = v.id AND vct.cargo_type = $1))
                AND ($2::text IS NULL OR EXISTS (
                        SELECT 1 FROM carrier_movement cm
                        WHERE cm.voyage_id = v.id
                          AND cm.seq_number = (SELECT MIN(seq_number) FROM carrier_movement WHERE voyage_id = v.id)
                          AND cm.departure_location_unlocode = $2))
                AND ($3::text IS NULL OR EXISTS (
                        SELECT 1 FROM carrier_movement cm
                        WHERE cm.voyage_id = v.id
                          AND cm.seq_number = (SELECT MAX(seq_number) FROM carrier_movement WHERE voyage_id = v.id)
                          AND cm.arrival_location_unlocode = $3))
              ORDER BY v.voyage_number",
        )
        .bind(cargo_type)
        .bind(origin)
        .bind(destination)
        .fetch_all(&self.pool)
        .await
        .map_err(backend)?;

        let mut voyages = Vec::with_capacity(ids.len());
        for row in ids {
            let id: i64 = row.try_get("id").map_err(backend)?;
            voyages.push(self.load_voyage(id).await?);
        }
        Ok(voyages)
    }

    async fn find_all(&self) -> Result<Vec<Voyage>, RepositoryError> {
        let ids = sqlx::query(r"SELECT id FROM voyage ORDER BY voyage_number")
            .fetch_all(&self.pool)
            .await
            .map_err(backend)?;
        let mut voyages = Vec::with_capacity(ids.len());
        for row in ids {
            let id: i64 = row.try_get("id").map_err(backend)?;
            voyages.push(self.load_voyage(id).await?);
        }
        Ok(voyages)
    }
}
