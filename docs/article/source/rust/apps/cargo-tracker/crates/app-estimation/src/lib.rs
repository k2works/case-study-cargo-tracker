//! Estimation Context のアプリケーション層。
//!
//! 輸送見積の作成（US01）ユースケースを提供する。ルート候補算出は ACL ポート
//! （`RouteCandidateProvider`）経由で既存 Routing（航海スケジュール）を参照し、
//! `domain-estimation` に他 BC の依存を持ち込まない（BC 独立）。

use async_trait::async_trait;
use chrono::NaiveDate;
use domain_estimation::{
    CargoType, Estimate, EstimateLocation, EstimateRepository, EstimationRepositoryError,
    RouteCandidate, Weight,
};
use rust_decimal::Decimal;

/// 見積ユースケースのエラー。
#[derive(Debug, thiserror::Error)]
pub enum EstimationServiceError {
    /// 入力が不正（同一地点・重量 0 以下・不正 UN/LOCODE 等）。
    #[error("入力が不正です: {0}")]
    InvalidInput(String),
    /// 永続化・外部連携の失敗。
    #[error("処理に失敗しました: {0}")]
    Backend(String),
}

impl From<EstimationRepositoryError> for EstimationServiceError {
    fn from(e: EstimationRepositoryError) -> Self {
        Self::Backend(e.to_string())
    }
}

/// ルート候補算出の要求（ACL 入力）。
#[derive(Debug, Clone)]
pub struct RouteCandidateQuery {
    /// 出発地 UN/LOCODE。
    pub origin: String,
    /// 目的地 UN/LOCODE。
    pub destination: String,
    /// 到着期限。
    pub arrival_deadline: NaiveDate,
    /// 貨物種別。
    pub cargo_type: CargoType,
    /// 重量（概算料金の算定に使用）。
    pub weight: Decimal,
}

/// ルート候補算出 ACL（Estimation 側ポート）。
///
/// 既存 Routing（航海スケジュール）から期限内到達可能なルート概算候補を算出する。
/// 実装は composition root で Routing をラップする（概算料金は重量ベースのスタブ・BC 独立）。
#[async_trait]
pub trait RouteCandidateProvider: Send + Sync {
    /// 要件に合致するルート候補を算出する（期限内 0 件なら空 `Vec`）。
    async fn find_candidates(
        &self,
        query: &RouteCandidateQuery,
    ) -> Result<Vec<RouteCandidate>, EstimationServiceError>;
}

/// 見積作成の入力（US01）。
#[derive(Debug, Clone)]
pub struct CreateEstimateInput {
    /// 出発地 UN/LOCODE。
    pub origin: String,
    /// 目的地 UN/LOCODE。
    pub destination: String,
    /// 到着期限。
    pub arrival_deadline: NaiveDate,
    /// 貨物種別。
    pub cargo_type: CargoType,
    /// 重量。
    pub weight: Decimal,
}

/// 見積作成の結果。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CreateEstimateOutcome {
    /// 発行された見積番号。
    pub estimate_id: String,
    /// 期限内に到達可能なルート候補があるか（US01 受入 5・無ければ画面で通知）。
    pub has_feasible_route: bool,
}

/// 見積作成ユースケース（US01）。
pub struct CreateEstimateService<R, P>
where
    R: EstimateRepository,
    P: RouteCandidateProvider,
{
    repository: R,
    route_provider: P,
}

impl<R, P> CreateEstimateService<R, P>
where
    R: EstimateRepository,
    P: RouteCandidateProvider,
{
    /// サービスを生成する。
    pub fn new(repository: R, route_provider: P) -> Self {
        Self {
            repository,
            route_provider,
        }
    }

    /// 見積を作成する（US01）。
    ///
    /// 手順:
    /// 1. 要件から `Estimate` を生成（同一地点・重量不正は `InvalidInput`）
    /// 2. Routing ACL でルート候補を算出し `replace_candidates`
    /// 3. 保存し、見積番号と実現可能性を返す
    ///
    /// # Errors
    ///
    /// 入力不正は `InvalidInput`、永続化・算出失敗は `Backend`。
    pub async fn create(
        &self,
        input: CreateEstimateInput,
    ) -> Result<CreateEstimateOutcome, EstimationServiceError> {
        let origin = EstimateLocation::new(&input.origin)
            .map_err(|e| EstimationServiceError::InvalidInput(e.to_string()))?;
        let destination = EstimateLocation::new(&input.destination)
            .map_err(|e| EstimationServiceError::InvalidInput(e.to_string()))?;
        let weight = Weight::new(input.weight)
            .map_err(|e| EstimationServiceError::InvalidInput(e.to_string()))?;
        let mut estimate = Estimate::create(
            origin,
            destination,
            input.arrival_deadline,
            input.cargo_type,
            weight,
        )
        .map_err(|e| EstimationServiceError::InvalidInput(e.to_string()))?;

        let candidates = self
            .route_provider
            .find_candidates(&RouteCandidateQuery {
                origin: input.origin,
                destination: input.destination,
                arrival_deadline: input.arrival_deadline,
                cargo_type: input.cargo_type,
                weight: input.weight,
            })
            .await?;
        estimate.replace_candidates(candidates);
        self.repository.save(&estimate).await?;

        Ok(CreateEstimateOutcome {
            estimate_id: estimate.estimate_id().as_str().to_string(),
            has_feasible_route: estimate.has_feasible_route(),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use domain_estimation::EstimateId;
    use mockall::mock;

    mock! {
        Repo {}
        #[async_trait]
        impl EstimateRepository for Repo {
            async fn save(&self, estimate: &Estimate) -> Result<(), EstimationRepositoryError>;
            async fn find_by_id(
                &self,
                id: &EstimateId,
            ) -> Result<Option<Estimate>, EstimationRepositoryError>;
            async fn find_all(&self) -> Result<Vec<Estimate>, EstimationRepositoryError>;
        }
    }

    mock! {
        Provider {}
        #[async_trait]
        impl RouteCandidateProvider for Provider {
            async fn find_candidates(
                &self,
                query: &RouteCandidateQuery,
            ) -> Result<Vec<RouteCandidate>, EstimationServiceError>;
        }
    }

    fn input() -> CreateEstimateInput {
        CreateEstimateInput {
            origin: "JPTYO".to_string(),
            destination: "DEHAM".to_string(),
            arrival_deadline: NaiveDate::from_ymd_opt(2026, 6, 30).unwrap(),
            cargo_type: CargoType::General,
            weight: Decimal::new(1200, 0),
        }
    }

    #[tokio::test]
    async fn 見積を保存し見積番号を発行する() {
        let mut repo = MockRepo::new();
        repo.expect_save().times(1).returning(|_| Ok(()));
        let mut provider = MockProvider::new();
        provider.expect_find_candidates().returning(|_| {
            Ok(vec![RouteCandidate::new(
                "V001",
                vec!["SGSIN".to_string()],
                14,
                Decimal::new(120000, 0),
                1,
            )])
        });

        let service = CreateEstimateService::new(repo, provider);
        let outcome = service.create(input()).await.expect("作成できるはず");
        assert!(outcome.estimate_id.starts_with("EST-"));
        assert!(outcome.has_feasible_route);
    }

    #[tokio::test]
    async fn 期限内ルートが無い場合を通知できる() {
        let mut repo = MockRepo::new();
        repo.expect_save().times(1).returning(|_| Ok(()));
        let mut provider = MockProvider::new();
        // 期限内候補 0 件。
        provider.expect_find_candidates().returning(|_| Ok(vec![]));

        let service = CreateEstimateService::new(repo, provider);
        let outcome = service.create(input()).await.expect("作成は成立");
        assert!(!outcome.has_feasible_route);
    }

    #[tokio::test]
    async fn 出発地と目的地が同一だと入力エラー() {
        let mut repo = MockRepo::new();
        repo.expect_save().never();
        let mut provider = MockProvider::new();
        provider.expect_find_candidates().never();

        let service = CreateEstimateService::new(repo, provider);
        let mut inp = input();
        inp.destination = "JPTYO".to_string();
        let result = service.create(inp).await;
        assert!(matches!(
            result,
            Err(EstimationServiceError::InvalidInput(_))
        ));
    }

    #[tokio::test]
    async fn 重量0以下は入力エラー() {
        let mut repo = MockRepo::new();
        repo.expect_save().never();
        let mut provider = MockProvider::new();
        provider.expect_find_candidates().never();

        let service = CreateEstimateService::new(repo, provider);
        let mut inp = input();
        inp.weight = Decimal::ZERO;
        let result = service.create(inp).await;
        assert!(matches!(
            result,
            Err(EstimationServiceError::InvalidInput(_))
        ));
    }
}
