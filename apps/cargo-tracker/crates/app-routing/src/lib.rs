//! Routing Context のアプリケーション層。
//!
//! 航海スケジュールの登録（US24）・更新（US25）・検索（US07）ユースケースを提供する。
//! ドメインの出力ポート `VoyageRepository` にのみ依存し、インフラ実装には依存しない。

use chrono::{DateTime, Utc};
use domain_routing::{
    CargoType, Carrier, CarrierMovement, RepositoryError, RoutingError, Schedule, VesselName,
    Voyage, VoyageNumber, VoyageRepository, VoyageSearchCriteria,
};
use shared_kernel::{Location, SharedKernelError};

/// 運送区間の入力（プレゼンテーション層からの生値）。
#[derive(Debug, Clone)]
pub struct MovementInput {
    /// 出発港（UN/LOCODE）。
    pub departure_unlocode: String,
    /// 到着港（UN/LOCODE）。
    pub arrival_unlocode: String,
    /// 出発日時。
    pub departure_time: DateTime<Utc>,
    /// 到着日時。
    pub arrival_time: DateTime<Utc>,
}

/// 航海登録・更新の入力。
#[derive(Debug, Clone)]
pub struct VoyageInput {
    /// 航海番号。
    pub voyage_number: String,
    /// 船名。
    pub vessel_name: String,
    /// 運送会社。
    pub carrier: String,
    /// 対応貨物種別（`GENERAL` / `HAZARDOUS` / `REFRIGERATED`）。
    pub cargo_types: Vec<String>,
    /// 運送区間（順序付き）。
    pub movements: Vec<MovementInput>,
}

/// 航海検索の入力（US07）。
#[derive(Debug, Clone, Default)]
pub struct VoyageSearchInput {
    /// 出発港（UN/LOCODE、任意）。
    pub origin: Option<String>,
    /// 到着港（UN/LOCODE、任意）。
    pub destination: Option<String>,
    /// 対応貨物種別（任意）。
    pub cargo_type: Option<String>,
}

/// 航海サービスのエラー。
#[derive(Debug, thiserror::Error)]
pub enum VoyageServiceError {
    /// ドメイン検証エラー。
    #[error(transparent)]
    Domain(#[from] RoutingError),
    /// 共有カーネル（UN/LOCODE）検証エラー。
    #[error(transparent)]
    Location(#[from] SharedKernelError),
    /// 航海番号が既に存在する。
    #[error("voyage already exists: {0}")]
    AlreadyExists(String),
    /// 更新対象の航海が存在しない。
    #[error("voyage not found: {0}")]
    NotFound(String),
    /// 永続化エラー。
    #[error("repository error: {0}")]
    Repository(String),
}

impl From<RepositoryError> for VoyageServiceError {
    fn from(e: RepositoryError) -> Self {
        Self::Repository(e.to_string())
    }
}

fn build_schedule(movements: &[MovementInput]) -> Result<Schedule, VoyageServiceError> {
    let mut legs = Vec::with_capacity(movements.len());
    for m in movements {
        let movement = CarrierMovement::new(
            Location::new(&m.departure_unlocode)?,
            Location::new(&m.arrival_unlocode)?,
            m.departure_time,
            m.arrival_time,
        )?;
        legs.push(movement);
    }
    Ok(Schedule::new(legs)?)
}

fn parse_cargo_types(values: &[String]) -> Result<Vec<CargoType>, VoyageServiceError> {
    values
        .iter()
        .map(|v| CargoType::parse(v).map_err(VoyageServiceError::from))
        .collect()
}

/// 航海の登録・更新ユースケース（US24 / US25）。
pub struct VoyageCommandService<R: VoyageRepository> {
    repository: R,
}

impl<R: VoyageRepository> VoyageCommandService<R> {
    /// サービスを生成する。
    pub fn new(repository: R) -> Self {
        Self { repository }
    }

    /// 航海スケジュールを新規登録する（US24）。
    ///
    /// # Errors
    ///
    /// 同一航海番号が既に存在する場合は `VoyageServiceError::AlreadyExists`、
    /// 入力が不正な場合は `Domain` / `Location`、永続化失敗時は `Repository`。
    pub async fn register(&self, input: VoyageInput) -> Result<VoyageNumber, VoyageServiceError> {
        let number = VoyageNumber::new(input.voyage_number)?;
        if self.repository.exists(&number).await? {
            return Err(VoyageServiceError::AlreadyExists(
                number.as_str().to_string(),
            ));
        }
        let voyage = Voyage::register(
            number.clone(),
            VesselName::new(input.vessel_name)?,
            Carrier::new(input.carrier)?,
            parse_cargo_types(&input.cargo_types)?,
            build_schedule(&input.movements)?,
        );
        self.repository.save(&voyage).await?;
        Ok(number)
    }

    /// 既存の航海スケジュールを最新情報で上書き更新する（US25）。
    ///
    /// # Errors
    ///
    /// 対象航海が存在しない場合は `VoyageServiceError::NotFound`。
    pub async fn update(&self, input: VoyageInput) -> Result<VoyageNumber, VoyageServiceError> {
        let number = VoyageNumber::new(input.voyage_number)?;
        let Some(mut voyage) = self.repository.find_by_voyage_number(&number).await? else {
            return Err(VoyageServiceError::NotFound(number.as_str().to_string()));
        };
        voyage.update(
            VesselName::new(input.vessel_name)?,
            Carrier::new(input.carrier)?,
            parse_cargo_types(&input.cargo_types)?,
            build_schedule(&input.movements)?,
        );
        self.repository.save(&voyage).await?;
        Ok(number)
    }
}

/// 航海の検索・参照ユースケース（US07・一覧表示）。
pub struct VoyageQueryService<R: VoyageRepository> {
    repository: R,
}

impl<R: VoyageRepository> VoyageQueryService<R> {
    /// サービスを生成する。
    pub fn new(repository: R) -> Self {
        Self { repository }
    }

    /// 全航海を返す（航路一覧の初期表示）。
    ///
    /// # Errors
    ///
    /// 永続化エラー時は `VoyageServiceError::Repository`。
    pub async fn list(&self) -> Result<Vec<Voyage>, VoyageServiceError> {
        Ok(self.repository.find_all().await?)
    }

    /// 航海番号で 1 件取得する（更新フォームの呼び出し）。
    ///
    /// # Errors
    ///
    /// 永続化エラー時は `VoyageServiceError::Repository`。
    pub async fn find(&self, number: &str) -> Result<Option<Voyage>, VoyageServiceError> {
        let number = VoyageNumber::new(number)?;
        Ok(self.repository.find_by_voyage_number(&number).await?)
    }

    /// 検索条件で航海を絞り込む（US07）。
    ///
    /// # Errors
    ///
    /// 検索条件の UN/LOCODE・貨物種別が不正な場合は `Location` / `Domain`。
    pub async fn search(
        &self,
        input: VoyageSearchInput,
    ) -> Result<Vec<Voyage>, VoyageServiceError> {
        let origin = input.origin.as_deref().map(Location::new).transpose()?;
        let destination = input
            .destination
            .as_deref()
            .map(Location::new)
            .transpose()?;
        let cargo_type = input
            .cargo_type
            .as_deref()
            .map(CargoType::parse)
            .transpose()?;
        let criteria = VoyageSearchCriteria {
            origin,
            destination,
            cargo_type,
        };
        Ok(self.repository.search(&criteria).await?)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use async_trait::async_trait;
    use std::sync::Mutex;

    /// テスト用インメモリリポジトリ。
    #[derive(Default)]
    struct InMemoryVoyageRepository {
        voyages: Mutex<Vec<Voyage>>,
    }

    #[async_trait]
    impl VoyageRepository for InMemoryVoyageRepository {
        async fn save(&self, voyage: &Voyage) -> Result<(), RepositoryError> {
            let mut store = self.voyages.lock().unwrap();
            if let Some(existing) = store
                .iter_mut()
                .find(|v| v.voyage_number() == voyage.voyage_number())
            {
                *existing = voyage.clone();
            } else {
                store.push(voyage.clone());
            }
            Ok(())
        }

        async fn find_by_voyage_number(
            &self,
            number: &VoyageNumber,
        ) -> Result<Option<Voyage>, RepositoryError> {
            Ok(self
                .voyages
                .lock()
                .unwrap()
                .iter()
                .find(|v| v.voyage_number() == number)
                .cloned())
        }

        async fn exists(&self, number: &VoyageNumber) -> Result<bool, RepositoryError> {
            Ok(self
                .voyages
                .lock()
                .unwrap()
                .iter()
                .any(|v| v.voyage_number() == number))
        }

        async fn search(
            &self,
            criteria: &VoyageSearchCriteria,
        ) -> Result<Vec<Voyage>, RepositoryError> {
            Ok(self
                .voyages
                .lock()
                .unwrap()
                .iter()
                .filter(|v| {
                    v.matches(
                        criteria.origin.as_ref(),
                        criteria.destination.as_ref(),
                        criteria.cargo_type,
                    )
                })
                .cloned()
                .collect())
        }

        async fn find_all(&self) -> Result<Vec<Voyage>, RepositoryError> {
            Ok(self.voyages.lock().unwrap().clone())
        }
    }

    fn input(number: &str, origin: &str, dest: &str, cargo_types: Vec<&str>) -> VoyageInput {
        VoyageInput {
            voyage_number: number.to_string(),
            vessel_name: "SAKURA MARU".to_string(),
            carrier: "Nippon Line".to_string(),
            cargo_types: cargo_types.into_iter().map(String::from).collect(),
            movements: vec![MovementInput {
                departure_unlocode: origin.to_string(),
                arrival_unlocode: dest.to_string(),
                departure_time: DateTime::parse_from_rfc3339("2026-04-01T18:00:00Z")
                    .unwrap()
                    .with_timezone(&Utc),
                arrival_time: DateTime::parse_from_rfc3339("2026-04-14T08:00:00Z")
                    .unwrap()
                    .with_timezone(&Utc),
            }],
        }
    }

    #[tokio::test]
    async fn 航海を新規登録できる() {
        let svc = VoyageCommandService::new(InMemoryVoyageRepository::default());
        let number = svc
            .register(input(
                "V0042",
                "JPOSA",
                "USLAX",
                vec!["GENERAL", "HAZARDOUS"],
            ))
            .await
            .expect("登録成功");
        assert_eq!(number.as_str(), "V0042");
    }

    #[tokio::test]
    async fn 同一航海番号の登録は重複エラーになる() {
        let repo = InMemoryVoyageRepository::default();
        let svc = VoyageCommandService::new(repo);
        svc.register(input("V0042", "JPOSA", "USLAX", vec!["GENERAL"]))
            .await
            .unwrap();
        let err = svc
            .register(input("V0042", "JPOSA", "USLAX", vec!["GENERAL"]))
            .await
            .unwrap_err();
        assert!(matches!(err, VoyageServiceError::AlreadyExists(_)));
    }

    #[tokio::test]
    async fn 存在しない航海の更新はnotfoundになる() {
        let svc = VoyageCommandService::new(InMemoryVoyageRepository::default());
        let err = svc
            .update(input("V9999", "JPOSA", "USLAX", vec!["GENERAL"]))
            .await
            .unwrap_err();
        assert!(matches!(err, VoyageServiceError::NotFound(_)));
    }

    #[tokio::test]
    async fn 出発が到着より後の登録はドメインエラーになる() {
        let svc = VoyageCommandService::new(InMemoryVoyageRepository::default());
        let mut bad = input("V0001", "JPOSA", "USLAX", vec!["GENERAL"]);
        bad.movements[0].departure_time = DateTime::parse_from_rfc3339("2026-04-20T00:00:00Z")
            .unwrap()
            .with_timezone(&Utc);
        let err = svc.register(bad).await.unwrap_err();
        assert!(matches!(err, VoyageServiceError::Domain(_)));
    }

    #[tokio::test]
    async fn 検索で貨物種別を絞り込める() {
        // 共有リポジトリを Arc で 2 サービスに渡せないため、登録→検索を同一 repo で行う
        let repo = InMemoryVoyageRepository::default();
        {
            let cmd = VoyageCommandService::new(&repo);
            cmd.register(input("V0001", "JPOSA", "USLAX", vec!["GENERAL"]))
                .await
                .unwrap();
            cmd.register(input("V0002", "JPYOK", "DEHAM", vec!["HAZARDOUS"]))
                .await
                .unwrap();
        }
        let query = VoyageQueryService::new(&repo);
        let result = query
            .search(VoyageSearchInput {
                origin: None,
                destination: None,
                cargo_type: Some("HAZARDOUS".to_string()),
            })
            .await
            .unwrap();
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].voyage_number().as_str(), "V0002");
    }

    // &R も VoyageRepository を満たすことで、テストで参照を共有できるようにする
    #[async_trait]
    impl VoyageRepository for &InMemoryVoyageRepository {
        async fn save(&self, voyage: &Voyage) -> Result<(), RepositoryError> {
            (**self).save(voyage).await
        }
        async fn find_by_voyage_number(
            &self,
            number: &VoyageNumber,
        ) -> Result<Option<Voyage>, RepositoryError> {
            (**self).find_by_voyage_number(number).await
        }
        async fn exists(&self, number: &VoyageNumber) -> Result<bool, RepositoryError> {
            (**self).exists(number).await
        }
        async fn search(
            &self,
            criteria: &VoyageSearchCriteria,
        ) -> Result<Vec<Voyage>, RepositoryError> {
            (**self).search(criteria).await
        }
        async fn find_all(&self) -> Result<Vec<Voyage>, RepositoryError> {
            (**self).find_all().await
        }
    }
}
