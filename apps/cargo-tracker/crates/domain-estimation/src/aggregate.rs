//! Estimation Context の集約ルート `Estimate`。

use crate::error::EstimationError;
use crate::value_objects::{
    CargoType, EstimateId, EstimateLocation, EstimateStatus, RouteCandidate, Weight,
};
use chrono::NaiveDate;
use serde::{Deserialize, Serialize};

/// 見積集約ルート。輸送要件とルート候補（`Vec`）を保持する。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Estimate {
    estimate_id: EstimateId,
    origin: EstimateLocation,
    destination: EstimateLocation,
    arrival_deadline: NaiveDate,
    cargo_type: CargoType,
    weight: Weight,
    status: EstimateStatus,
    candidates: Vec<RouteCandidate>,
}

impl Estimate {
    /// 見積を新規作成する（US01）。ルート候補は空で開始し、`replace_candidates` で設定する。
    ///
    /// # Errors
    ///
    /// 出発地と目的地が同一の場合は `SameOriginAndDestination` を返す。
    pub fn create(
        origin: EstimateLocation,
        destination: EstimateLocation,
        arrival_deadline: NaiveDate,
        cargo_type: CargoType,
        weight: Weight,
    ) -> Result<Self, EstimationError> {
        if origin.un_locode() == destination.un_locode() {
            return Err(EstimationError::SameOriginAndDestination);
        }
        Ok(Self {
            estimate_id: EstimateId::generate(),
            origin,
            destination,
            arrival_deadline,
            cargo_type,
            weight,
            status: EstimateStatus::Created,
            candidates: Vec::new(),
        })
    }

    /// 永続化済みデータから再構築する。
    #[allow(clippy::too_many_arguments)]
    #[must_use]
    pub fn reconstruct(
        estimate_id: EstimateId,
        origin: EstimateLocation,
        destination: EstimateLocation,
        arrival_deadline: NaiveDate,
        cargo_type: CargoType,
        weight: Weight,
        status: EstimateStatus,
        candidates: Vec<RouteCandidate>,
    ) -> Self {
        Self {
            estimate_id,
            origin,
            destination,
            arrival_deadline,
            cargo_type,
            weight,
            status,
            candidates,
        }
    }

    /// ルート候補を一括入替する（`rank` 昇順で保持）。
    pub fn replace_candidates(&mut self, mut candidates: Vec<RouteCandidate>) {
        candidates.sort_by_key(RouteCandidate::rank);
        self.candidates = candidates;
    }

    /// 見積 ID。
    #[must_use]
    pub fn estimate_id(&self) -> &EstimateId {
        &self.estimate_id
    }

    /// 出発地。
    #[must_use]
    pub fn origin(&self) -> &EstimateLocation {
        &self.origin
    }

    /// 目的地。
    #[must_use]
    pub fn destination(&self) -> &EstimateLocation {
        &self.destination
    }

    /// 到着期限。
    #[must_use]
    pub fn arrival_deadline(&self) -> NaiveDate {
        self.arrival_deadline
    }

    /// 貨物種別。
    #[must_use]
    pub fn cargo_type(&self) -> CargoType {
        self.cargo_type
    }

    /// 重量。
    #[must_use]
    pub fn weight(&self) -> Weight {
        self.weight
    }

    /// 見積状態。
    #[must_use]
    pub fn status(&self) -> EstimateStatus {
        self.status
    }

    /// ルート候補。
    #[must_use]
    pub fn candidates(&self) -> &[RouteCandidate] {
        &self.candidates
    }

    /// 期限内に到達可能なルート候補が 1 件以上あるか（US01 受入 5）。
    #[must_use]
    pub fn has_feasible_route(&self) -> bool {
        !self.candidates.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rust_decimal::Decimal;

    fn loc(code: &str) -> EstimateLocation {
        EstimateLocation::new(code).unwrap()
    }

    fn weight() -> Weight {
        Weight::new(Decimal::new(1200, 0)).unwrap()
    }

    fn deadline() -> NaiveDate {
        NaiveDate::from_ymd_opt(2026, 6, 30).unwrap()
    }

    #[test]
    fn 見積は要件を保持し見積番号が発行される() {
        let est = Estimate::create(
            loc("JPTYO"),
            loc("DEHAM"),
            deadline(),
            CargoType::General,
            weight(),
        )
        .expect("作成できるはず");
        assert!(est.estimate_id().as_str().starts_with("EST-"));
        assert_eq!(est.origin().un_locode(), "JPTYO");
        assert_eq!(est.destination().un_locode(), "DEHAM");
        assert_eq!(est.status(), EstimateStatus::Created);
        assert!(!est.has_feasible_route());
    }

    #[test]
    fn 出発地と目的地が同一だとエラー() {
        let result = Estimate::create(
            loc("JPTYO"),
            loc("JPTYO"),
            deadline(),
            CargoType::General,
            weight(),
        );
        assert_eq!(result, Err(EstimationError::SameOriginAndDestination));
    }

    #[test]
    fn ルート候補を入替するとrank昇順で保持され実現可能判定が真になる() {
        let mut est = Estimate::create(
            loc("JPTYO"),
            loc("DEHAM"),
            deadline(),
            CargoType::General,
            weight(),
        )
        .unwrap();
        est.replace_candidates(vec![
            RouteCandidate::new("V002", vec![], 20, Decimal::new(90000, 0), 2),
            RouteCandidate::new(
                "V001",
                vec!["SGSIN".to_string()],
                14,
                Decimal::new(120000, 0),
                1,
            ),
        ]);
        assert!(est.has_feasible_route());
        assert_eq!(est.candidates()[0].voyage_number(), "V001");
        assert_eq!(est.candidates()[1].voyage_number(), "V002");
    }
}
