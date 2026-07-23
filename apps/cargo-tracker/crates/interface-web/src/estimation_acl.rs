//! Estimation Context の ACL アダプター（composition 層）。
//!
//! `app-estimation` の `RouteCandidateProvider` を既存 Routing（航海スケジュール）へ橋渡しする。
//! ドメインクレート同士は依存せず、結線をここに閉じ込める（BC 独立）。概算料金は重量ベースの
//! スタブ（domain-model 根拠。将来の外部ルーティング連携で差し替え）。

use app_estimation::{EstimationServiceError, RouteCandidateProvider, RouteCandidateQuery};
use async_trait::async_trait;
use domain_estimation::{CargoType as EstCargoType, RouteCandidate};
use domain_routing::VoyageRepository;
use rust_decimal::Decimal;
use std::sync::Arc;

/// 既存 Routing を用いてルート概算候補を算出する ACL アダプター。
pub struct RoutingRouteCandidateProvider {
    voyage_repo: Arc<dyn VoyageRepository>,
}

impl RoutingRouteCandidateProvider {
    /// アダプターを生成する。
    #[must_use]
    pub fn new(voyage_repo: Arc<dyn VoyageRepository>) -> Self {
        Self { voyage_repo }
    }
}

/// 貨物種別（Estimation）→ 文字列（Routing の `CargoType::as_str` と一致）。
fn cargo_type_str(t: EstCargoType) -> &'static str {
    match t {
        EstCargoType::General => "GENERAL",
        EstCargoType::Hazardous => "HAZARDOUS",
        EstCargoType::Refrigerated => "REFRIGERATED",
    }
}

/// 概算料金スタブの基本料金（円）。
const BASE_FARE_YEN: i64 = 50_000;
/// 概算料金スタブの重量単価（円/kg）。
const PER_KG_YEN: i64 = 80;
/// 概算料金スタブの日数係数（円/日）。
const PER_DAY_YEN: i64 = 2_000;

/// 概算料金のスタブ計算（基本料金 + 重量 × 単価 + 日数 × 係数）。
///
/// 将来の外部ルーティング連携で差し替える暫定ロジック。純粋関数として単体テスト可能。
fn estimate_cost(weight: Decimal, transit_days: i64) -> Decimal {
    Decimal::from(BASE_FARE_YEN)
        + weight * Decimal::from(PER_KG_YEN)
        + Decimal::from(transit_days) * Decimal::from(PER_DAY_YEN)
}

#[async_trait]
impl RouteCandidateProvider for RoutingRouteCandidateProvider {
    async fn find_candidates(
        &self,
        query: &RouteCandidateQuery,
    ) -> Result<Vec<RouteCandidate>, EstimationServiceError> {
        let voyages = self
            .voyage_repo
            .find_all()
            .await
            .map_err(|e| EstimationServiceError::Backend(e.to_string()))?;
        let want_type = cargo_type_str(query.cargo_type);
        // 期限までの UTC 締め（当日 23:59:59）。
        let deadline_end = query
            .arrival_deadline
            .and_hms_opt(23, 59, 59)
            .map(|naive| naive.and_utc());

        let mut candidates: Vec<RouteCandidate> = Vec::new();
        for v in &voyages {
            // 直行のみ（origin→destination が要件に一致）。
            if v.origin().code() != query.origin || v.destination().code() != query.destination {
                continue;
            }
            // 貨物種別対応。
            if !v
                .supported_cargo_types()
                .iter()
                .any(|c| c.as_str() == want_type)
            {
                continue;
            }
            let movements = v.schedule().carrier_movements();
            // 区間が無い航海は概算不能のためスキップ（ACL 境界での防御）。
            let (Some(first), Some(last)) = (movements.first(), movements.last()) else {
                continue;
            };
            let departure = first.departure_time();
            let arrival = last.arrival_time();
            // 期限内到達のみ。
            if let Some(end) = deadline_end
                && arrival > end
            {
                continue;
            }
            let transit_days = (arrival - departure).num_days().max(0);
            let transit_ports: Vec<String> = movements
                .iter()
                .take(movements.len().saturating_sub(1))
                .map(|m| m.arrival_location().code().to_string())
                .collect();
            candidates.push(RouteCandidate::new(
                v.voyage_number().as_str(),
                transit_ports,
                i32::try_from(transit_days).unwrap_or(i32::MAX),
                estimate_cost(query.weight, transit_days),
                0,
            ));
        }
        // 所要日数昇順に rank を付与。
        candidates.sort_by_key(RouteCandidate::transit_days);
        let ranked = candidates
            .into_iter()
            .enumerate()
            .map(|(i, c)| {
                RouteCandidate::new(
                    c.voyage_number().to_string(),
                    c.transit_ports().to_vec(),
                    c.transit_days(),
                    c.estimated_cost(),
                    i32::try_from(i + 1).unwrap_or(i32::MAX),
                )
            })
            .collect();
        Ok(ranked)
    }
}

#[cfg(test)]
mod tests {
    use super::{BASE_FARE_YEN, PER_DAY_YEN, PER_KG_YEN, estimate_cost};
    use rust_decimal::Decimal;

    #[test]
    fn 概算料金は基本料金と重量単価と日数係数の合計になる() {
        // 1200kg・7 日 → 50000 + 1200×80 + 7×2000 = 160000。
        let cost = estimate_cost(Decimal::new(1200, 0), 7);
        assert_eq!(cost, Decimal::new(160_000, 0));
    }

    #[test]
    fn 重量0_日数0でも基本料金が下限になる() {
        let cost = estimate_cost(Decimal::ZERO, 0);
        assert_eq!(cost, Decimal::from(BASE_FARE_YEN));
    }

    #[test]
    fn 小数重量も単価に比例する() {
        // 10.5kg・0 日 → 50000 + 10.5×80 = 50840。
        let cost = estimate_cost(Decimal::new(105, 1), 0);
        assert_eq!(
            cost,
            Decimal::from(BASE_FARE_YEN) + Decimal::new(105, 1) * Decimal::from(PER_KG_YEN)
        );
        // 係数定数が意図通り使われていることを日数側でも確認。
        let with_day = estimate_cost(Decimal::ZERO, 3);
        assert_eq!(
            with_day,
            Decimal::from(BASE_FARE_YEN) + Decimal::from(3 * PER_DAY_YEN)
        );
    }
}
