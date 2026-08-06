//! Routing Context のアプリケーション層。
//!
//! 航海スケジュールの登録（US24）・更新（US25）・検索（US07）ユースケースを提供する。
//! ドメインの出力ポート `VoyageRepository` にのみ依存し、インフラ実装には依存しない。

use chrono::{DateTime, NaiveDate, Utc};
use domain_routing::{
    CargoSpec, CargoSpecProvider, CargoType, Carrier, CarrierMovement, RepositoryError,
    RouteCandidate, RouteCandidateCalculator, RoutingError, Schedule, SelectedRouteRepository,
    VesselName, Voyage, VoyageNumber, VoyageRepository, VoyageSearchCriteria,
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
    /// 出発日の下限（`YYYY-MM-DD`、任意・US07）。
    pub departure_from: Option<String>,
    /// 出発日の上限（`YYYY-MM-DD`、任意・US07）。
    pub departure_to: Option<String>,
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
    /// 日付の形式が不正な場合。
    #[error("invalid date: {0}")]
    InvalidDate(String),
    /// ACL（貨物仕様取得）エラー。
    #[error("acl error: {0}")]
    Acl(String),
    /// 指定予約が見つからない場合（経路設計の対象予約が存在しない）。
    #[error("booking not found: {0}")]
    BookingNotFound(String),
    /// 選択された経路候補のインデックスが範囲外の場合。
    #[error("invalid route candidate index: {0}")]
    InvalidCandidate(usize),
    /// 期限超過の候補を確定しようとした場合（誤確定防止・IT3 Try #3）。
    #[error("cannot confirm a candidate that exceeds the arrival deadline")]
    CandidateExpired,
    /// 表示時と確定時で候補（航海番号列）が一致しない場合（TOCTOU 対策・IT3 Try #4）。
    #[error("selected candidate no longer matches the displayed route")]
    CandidateMismatch,
}

/// `YYYY-MM-DD` 文字列を `NaiveDate` にパースする（空・未指定は `None`）。
fn parse_date(value: Option<&str>) -> Result<Option<NaiveDate>, VoyageServiceError> {
    match value.map(str::trim).filter(|s| !s.is_empty()) {
        Some(s) => NaiveDate::parse_from_str(s, "%Y-%m-%d")
            .map(Some)
            .map_err(|_| VoyageServiceError::InvalidDate(s.to_string())),
        None => Ok(None),
    }
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
        let departure_from = parse_date(input.departure_from.as_deref())?;
        let departure_to = parse_date(input.departure_to.as_deref())?;
        let criteria = VoyageSearchCriteria {
            origin,
            destination,
            cargo_type,
            departure_from,
            departure_to,
        };
        Ok(self.repository.search(&criteria).await?)
    }
}

/// 経路条件の調整入力（US10）。期限延長・貨物種別変更で再算出する。
///
/// 経由地追加はアルゴリズム改修を要するため本 IT ではスコープ外（計画リスク表参照）。
#[derive(Debug, Clone, Default)]
pub struct RouteAdjustment {
    /// 到着期限を延長する日数（任意）。
    pub extend_deadline_days: Option<i64>,
    /// 貨物種別の上書き（`GENERAL`/`HAZARDOUS`/`REFRIGERATED`、任意）。
    pub cargo_type_override: Option<String>,
}

impl RouteAdjustment {
    /// 調整が何も指定されていない（元の条件と同じ）か。
    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.extend_deadline_days.is_none() && self.cargo_type_override.is_none()
    }
}

/// 経路候補算出ユースケース（US08）。予約番号から貨物仕様を取得し、
/// 制約を満たす航海を検索して経路候補を算出する。
pub struct RoutePlanningService<R, P, S>
where
    R: VoyageRepository,
    P: CargoSpecProvider,
    S: SelectedRouteRepository,
{
    repository: R,
    spec_provider: P,
    selected_routes: S,
    calculator: RouteCandidateCalculator,
}

impl<R, P, S> RoutePlanningService<R, P, S>
where
    R: VoyageRepository,
    P: CargoSpecProvider,
    S: SelectedRouteRepository,
{
    /// サービスを生成する。
    pub fn new(repository: R, spec_provider: P, selected_routes: S) -> Self {
        Self {
            repository,
            spec_provider,
            selected_routes,
            calculator: RouteCandidateCalculator::default(),
        }
    }

    /// 経路候補から 1 件を選択して確定し、予約番号に紐づけて永続化する（US09）。
    ///
    /// `expected_voyage_numbers` が空でない場合、確定時に再算出した候補の航海番号列と
    /// 照合し、表示時と異なる場合は `CandidateMismatch` を返す（TOCTOU 対策・IT3 Try #4）。
    /// 期限超過の候補は誤確定防止のため `CandidateExpired` で拒否する（IT3 Try #3）。
    ///
    /// # Errors
    ///
    /// 予約が無い場合は `BookingNotFound`、選択インデックスが範囲外なら `InvalidCandidate`、
    /// 期限超過候補は `CandidateExpired`、候補不一致は `CandidateMismatch`、
    /// 永続化失敗時は `Repository`。
    pub async fn confirm_route(
        &self,
        booking_id: &str,
        candidate_index: usize,
        expected_voyage_numbers: &[String],
    ) -> Result<(), VoyageServiceError> {
        let (spec, candidates) = self.plan_routes(booking_id).await?;
        let selected = candidates
            .get(candidate_index)
            .ok_or(VoyageServiceError::InvalidCandidate(candidate_index))?;

        // Try #4: 表示時と確定時で候補（航海番号列）が一致するか照合する。
        if !expected_voyage_numbers.is_empty() {
            let actual: Vec<String> = selected
                .voyage_numbers()
                .iter()
                .map(|n| n.as_str().to_string())
                .collect();
            if actual != expected_voyage_numbers {
                return Err(VoyageServiceError::CandidateMismatch);
            }
        }

        // Try #3: 期限超過候補の確定を拒否する（誤確定防止）。
        if !selected.within_deadline(spec.arrival_deadline) {
            return Err(VoyageServiceError::CandidateExpired);
        }

        self.selected_routes.save(booking_id, selected).await?;
        Ok(())
    }

    /// 予約番号に紐づく貨物仕様を取得する（画面上部の貨物仕様表示・US07）。
    ///
    /// # Errors
    ///
    /// ACL エラー時は `Acl`、対象予約が無い場合は `BookingNotFound`。
    pub async fn cargo_spec(&self, booking_id: &str) -> Result<CargoSpec, VoyageServiceError> {
        self.spec_provider
            .find_cargo_spec(booking_id)
            .await
            .map_err(|e| VoyageServiceError::Acl(e.to_string()))?
            .ok_or_else(|| VoyageServiceError::BookingNotFound(booking_id.to_string()))
    }

    /// 予約番号をもとに経路候補を推奨順で算出する（US08）。
    ///
    /// 貨物仕様（出発地・目的地・期限・貨物種別）を取得し、貨物種別に対応する航海から
    /// 経路候補を算出する。期限超過候補も含むが推奨順では後方に並ぶ。
    ///
    /// # Errors
    ///
    /// 予約が無い場合は `BookingNotFound`、永続化・ACL 失敗時はそれぞれ `Repository`/`Acl`。
    pub async fn plan_routes(
        &self,
        booking_id: &str,
    ) -> Result<(CargoSpec, Vec<RouteCandidate>), VoyageServiceError> {
        let spec = self.cargo_spec(booking_id).await?;
        let candidates = self.calculate_for_spec(&spec).await?;
        Ok((spec, candidates))
    }

    /// 条件を調整して経路候補を再算出する（US10）。
    ///
    /// 期限延長・貨物種別変更を貨物仕様に適用してから経路候補を算出する。調整後の
    /// 貨物仕様と候補を返し、0 件なら呼び出し側が条件協議依頼へ導く。
    ///
    /// # Errors
    ///
    /// 予約が無い場合は `BookingNotFound`、貨物種別が不正なら `Domain`、
    /// 永続化・ACL 失敗時はそれぞれ `Repository`/`Acl`。
    pub async fn plan_routes_adjusted(
        &self,
        booking_id: &str,
        adjustment: &RouteAdjustment,
    ) -> Result<(CargoSpec, Vec<RouteCandidate>), VoyageServiceError> {
        let mut spec = self.cargo_spec(booking_id).await?;
        if let Some(days) = adjustment.extend_deadline_days {
            spec.arrival_deadline += chrono::Duration::days(days);
        }
        if let Some(ct) = adjustment.cargo_type_override.as_deref() {
            spec.cargo_type = CargoType::parse(ct)?;
        }
        let candidates = self.calculate_for_spec(&spec).await?;
        Ok((spec, candidates))
    }

    /// 貨物仕様から経路候補を算出する内部処理。
    ///
    /// IT3 Try #2: `find_all` 全件ロードを廃し、貨物種別で SQL 絞り込みした航海のみを
    /// 探索対象にする（多段接続のため出発地では絞れないが、貨物種別は安全に絞れる）。
    async fn calculate_for_spec(
        &self,
        spec: &CargoSpec,
    ) -> Result<Vec<RouteCandidate>, VoyageServiceError> {
        let criteria = VoyageSearchCriteria {
            origin: None,
            destination: None,
            cargo_type: Some(spec.cargo_type),
            departure_from: None,
            departure_to: None,
        };
        let voyages = self.repository.search(&criteria).await?;
        Ok(self.calculator.calculate(
            &spec.origin,
            &spec.destination,
            spec.arrival_deadline,
            spec.cargo_type,
            &voyages,
        ))
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
                cargo_type: Some("HAZARDOUS".to_string()),
                ..Default::default()
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

    /// テスト用の貨物仕様プロバイダ（固定の 1 予約を返す）。
    struct FixedCargoSpecProvider {
        booking_id: String,
        spec: CargoSpec,
    }

    #[async_trait]
    impl CargoSpecProvider for FixedCargoSpecProvider {
        async fn find_cargo_spec(
            &self,
            booking_id: &str,
        ) -> Result<Option<CargoSpec>, domain_routing::AclError> {
            if booking_id == self.booking_id {
                Ok(Some(self.spec.clone()))
            } else {
                Ok(None)
            }
        }
    }

    fn spec(origin: &str, dest: &str, deadline: &str, cargo: CargoType) -> CargoSpec {
        CargoSpec {
            origin: Location::new(origin).unwrap(),
            destination: Location::new(dest).unwrap(),
            arrival_deadline: NaiveDate::parse_from_str(deadline, "%Y-%m-%d").unwrap(),
            cargo_type: cargo,
        }
    }

    /// テスト用の確定経路リポジトリ（保存された予約番号を記録する）。
    #[derive(Default)]
    struct InMemorySelectedRoutes {
        saved: Mutex<Vec<String>>,
    }

    #[async_trait]
    impl SelectedRouteRepository for InMemorySelectedRoutes {
        async fn save(
            &self,
            booking_id: &str,
            _route: &RouteCandidate,
        ) -> Result<(), RepositoryError> {
            self.saved.lock().unwrap().push(booking_id.to_string());
            Ok(())
        }
        async fn exists(&self, booking_id: &str) -> Result<bool, RepositoryError> {
            Ok(self.saved.lock().unwrap().iter().any(|b| b == booking_id))
        }
    }

    #[async_trait]
    impl SelectedRouteRepository for &InMemorySelectedRoutes {
        async fn save(
            &self,
            booking_id: &str,
            route: &RouteCandidate,
        ) -> Result<(), RepositoryError> {
            (**self).save(booking_id, route).await
        }
        async fn exists(&self, booking_id: &str) -> Result<bool, RepositoryError> {
            (**self).exists(booking_id).await
        }
    }

    #[tokio::test]
    async fn 予約番号から貨物仕様と経路候補を算出する() {
        let repo = InMemoryVoyageRepository::default();
        let cmd = VoyageCommandService::new(&repo);
        cmd.register(input("V0001", "JPOSA", "USLAX", vec!["GENERAL"]))
            .await
            .unwrap();

        let provider = FixedCargoSpecProvider {
            booking_id: "BKG-1".to_string(),
            spec: spec("JPOSA", "USLAX", "2026-04-20", CargoType::General),
        };
        let selected = InMemorySelectedRoutes::default();
        let service = RoutePlanningService::new(&repo, provider, &selected);
        let (spec, candidates) = service.plan_routes("BKG-1").await.expect("plan");
        assert_eq!(spec.origin.code(), "JPOSA");
        assert_eq!(candidates.len(), 1);
        assert_eq!(candidates[0].leg_count(), 1);
        assert_eq!(candidates[0].voyage_numbers()[0].as_str(), "V0001");
    }

    #[tokio::test]
    async fn 存在しない予約番号はbookingnotfoundになる() {
        let repo = InMemoryVoyageRepository::default();
        let provider = FixedCargoSpecProvider {
            booking_id: "BKG-1".to_string(),
            spec: spec("JPOSA", "USLAX", "2026-04-20", CargoType::General),
        };
        let selected = InMemorySelectedRoutes::default();
        let service = RoutePlanningService::new(&repo, provider, &selected);
        let err = service.plan_routes("BKG-UNKNOWN").await.unwrap_err();
        assert!(matches!(err, VoyageServiceError::BookingNotFound(_)));
    }

    #[tokio::test]
    async fn 経路を選択して確定すると保存される() {
        let repo = InMemoryVoyageRepository::default();
        let cmd = VoyageCommandService::new(&repo);
        cmd.register(input("V0001", "JPOSA", "USLAX", vec!["GENERAL"]))
            .await
            .unwrap();
        let provider = FixedCargoSpecProvider {
            booking_id: "BKG-1".to_string(),
            spec: spec("JPOSA", "USLAX", "2026-04-20", CargoType::General),
        };
        let selected = InMemorySelectedRoutes::default();
        let service = RoutePlanningService::new(&repo, provider, &selected);

        service
            .confirm_route("BKG-1", 0, &[])
            .await
            .expect("confirm");
        assert!(selected.exists("BKG-1").await.unwrap());
    }

    #[tokio::test]
    async fn 期限超過候補の確定は拒否される() {
        let repo = InMemoryVoyageRepository::default();
        let cmd = VoyageCommandService::new(&repo);
        cmd.register(input("V0001", "JPOSA", "USLAX", vec!["GENERAL"]))
            .await
            .unwrap();
        // 航海到着（2026-04-14）より前の期限にして期限超過候補を作る。
        let provider = FixedCargoSpecProvider {
            booking_id: "BKG-1".to_string(),
            spec: spec("JPOSA", "USLAX", "2026-04-10", CargoType::General),
        };
        let selected = InMemorySelectedRoutes::default();
        let service = RoutePlanningService::new(&repo, provider, &selected);

        let err = service.confirm_route("BKG-1", 0, &[]).await.unwrap_err();
        assert!(matches!(err, VoyageServiceError::CandidateExpired));
        assert!(!selected.exists("BKG-1").await.unwrap());
    }

    #[tokio::test]
    async fn 表示時と異なる航海番号列の確定は拒否される() {
        let repo = InMemoryVoyageRepository::default();
        let cmd = VoyageCommandService::new(&repo);
        cmd.register(input("V0001", "JPOSA", "USLAX", vec!["GENERAL"]))
            .await
            .unwrap();
        let provider = FixedCargoSpecProvider {
            booking_id: "BKG-1".to_string(),
            spec: spec("JPOSA", "USLAX", "2026-04-20", CargoType::General),
        };
        let selected = InMemorySelectedRoutes::default();
        let service = RoutePlanningService::new(&repo, provider, &selected);

        // 表示時は別の航海（V9999）だったと主張 → 不一致で拒否。
        let err = service
            .confirm_route("BKG-1", 0, &["V9999".to_string()])
            .await
            .unwrap_err();
        assert!(matches!(err, VoyageServiceError::CandidateMismatch));

        // 実際の候補（V0001）と一致すれば確定できる。
        service
            .confirm_route("BKG-1", 0, &["V0001".to_string()])
            .await
            .expect("confirm");
        assert!(selected.exists("BKG-1").await.unwrap());
    }

    #[tokio::test]
    async fn 期限延長で再算出すると期限内候補になる() {
        let repo = InMemoryVoyageRepository::default();
        let cmd = VoyageCommandService::new(&repo);
        cmd.register(input("V0001", "JPOSA", "USLAX", vec!["GENERAL"]))
            .await
            .unwrap();
        // 期限超過（到着 04-14 に対し期限 04-10）。
        let provider = FixedCargoSpecProvider {
            booking_id: "BKG-1".to_string(),
            spec: spec("JPOSA", "USLAX", "2026-04-10", CargoType::General),
        };
        let selected = InMemorySelectedRoutes::default();
        let service = RoutePlanningService::new(&repo, provider, &selected);

        // 調整前は期限超過。
        let (s0, before) = service.plan_routes("BKG-1").await.unwrap();
        assert!(!before[0].within_deadline(s0.arrival_deadline));

        // 10 日延長すると期限内になる。
        let adjustment = RouteAdjustment {
            extend_deadline_days: Some(10),
            cargo_type_override: None,
        };
        let (s1, after) = service
            .plan_routes_adjusted("BKG-1", &adjustment)
            .await
            .unwrap();
        assert!(after[0].within_deadline(s1.arrival_deadline));
    }

    #[tokio::test]
    async fn 範囲外の候補選択はinvalidcandidateになる() {
        let repo = InMemoryVoyageRepository::default();
        let cmd = VoyageCommandService::new(&repo);
        cmd.register(input("V0001", "JPOSA", "USLAX", vec!["GENERAL"]))
            .await
            .unwrap();
        let provider = FixedCargoSpecProvider {
            booking_id: "BKG-1".to_string(),
            spec: spec("JPOSA", "USLAX", "2026-04-20", CargoType::General),
        };
        let selected = InMemorySelectedRoutes::default();
        let service = RoutePlanningService::new(&repo, provider, &selected);

        let err = service.confirm_route("BKG-1", 99, &[]).await.unwrap_err();
        assert!(matches!(err, VoyageServiceError::InvalidCandidate(_)));
    }
}
