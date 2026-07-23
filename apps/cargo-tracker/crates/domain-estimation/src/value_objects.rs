//! Estimation Context の値オブジェクトと列挙型。

use crate::error::EstimationError;
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use shared_kernel::Location;

/// 見積 ID（UUID ベースの一意識別子）。
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct EstimateId(String);

impl EstimateId {
    /// 見積 ID を生成する（`EST-` プレフィックス + UUID）。
    #[must_use]
    pub fn generate() -> Self {
        Self(format!("EST-{}", uuid::Uuid::new_v4().simple()))
    }

    /// 文字列から見積 ID を復元する。
    #[must_use]
    pub fn from_string(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 貨物種別（Booking Context と共通の 3 値）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum CargoType {
    /// 一般貨物。
    General,
    /// 危険物。
    Hazardous,
    /// 冷凍・冷蔵貨物。
    Refrigerated,
}

impl CargoType {
    /// 永続化用の文字列表現。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::General => "GENERAL",
            Self::Hazardous => "HAZARDOUS",
            Self::Refrigerated => "REFRIGERATED",
        }
    }

    /// 文字列から復元する。未知の値は `General`。
    #[must_use]
    pub fn from_str_or_general(value: &str) -> Self {
        match value {
            "HAZARDOUS" => Self::Hazardous,
            "REFRIGERATED" => Self::Refrigerated,
            _ => Self::General,
        }
    }
}

/// 見積状態。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum EstimateStatus {
    /// 作成済。
    Created,
    /// 期限切れ。
    Expired,
}

impl EstimateStatus {
    /// 永続化用の文字列表現。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Created => "CREATED",
            Self::Expired => "EXPIRED",
        }
    }

    /// 文字列から復元する。未知の値は `Created`。
    #[must_use]
    pub fn from_str_or_created(value: &str) -> Self {
        match value {
            "EXPIRED" => Self::Expired,
            _ => Self::Created,
        }
    }
}

/// 貨物重量（0 より大きい）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Weight(Decimal);

impl Weight {
    /// 重量を生成する。
    ///
    /// # Errors
    ///
    /// 0 以下の場合は `EstimationError::InvalidWeight` を返す。
    pub fn new(value: Decimal) -> Result<Self, EstimationError> {
        if value <= Decimal::ZERO {
            return Err(EstimationError::InvalidWeight);
        }
        Ok(Self(value))
    }

    /// 値を返す。
    #[must_use]
    pub fn value(&self) -> Decimal {
        self.0
    }
}

/// ルート候補（見積に紐づく輸送ルートの概算・不変値オブジェクト）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RouteCandidate {
    voyage_number: String,
    transit_ports: Vec<String>,
    transit_days: i32,
    estimated_cost: Decimal,
    rank: i32,
}

impl RouteCandidate {
    /// ルート候補を生成する。
    #[must_use]
    pub fn new(
        voyage_number: impl Into<String>,
        transit_ports: Vec<String>,
        transit_days: i32,
        estimated_cost: Decimal,
        rank: i32,
    ) -> Self {
        Self {
            voyage_number: voyage_number.into(),
            transit_ports,
            transit_days,
            estimated_cost,
            rank,
        }
    }

    /// 航海番号。
    #[must_use]
    pub fn voyage_number(&self) -> &str {
        &self.voyage_number
    }

    /// 経由港。
    #[must_use]
    pub fn transit_ports(&self) -> &[String] {
        &self.transit_ports
    }

    /// 所要日数。
    #[must_use]
    pub fn transit_days(&self) -> i32 {
        self.transit_days
    }

    /// 概算料金。
    #[must_use]
    pub fn estimated_cost(&self) -> Decimal {
        self.estimated_cost
    }

    /// 推奨順位。
    #[must_use]
    pub fn rank(&self) -> i32 {
        self.rank
    }
}

/// 位置（共有カーネル `Location` を包む）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct EstimateLocation(Location);

impl EstimateLocation {
    /// UN/LOCODE 文字列から生成する。
    ///
    /// # Errors
    ///
    /// UN/LOCODE 形式が不正な場合は `EstimationError::InvalidLocation` を返す。
    pub fn new(un_locode: &str) -> Result<Self, EstimationError> {
        Location::new(un_locode)
            .map(Self)
            .map_err(|_| EstimationError::InvalidLocation(un_locode.to_string()))
    }

    /// UN/LOCODE 文字列を返す。
    #[must_use]
    pub fn un_locode(&self) -> &str {
        self.0.code()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 見積idは_est_プレフィックスで生成される() {
        assert!(EstimateId::generate().as_str().starts_with("EST-"));
    }

    #[test]
    fn 重量は0以下だとエラー() {
        assert!(Weight::new(Decimal::new(12, 1)).is_ok());
        assert_eq!(
            Weight::new(Decimal::ZERO),
            Err(EstimationError::InvalidWeight)
        );
    }

    #[test]
    fn 見積状態は文字列と相互変換できる() {
        assert_eq!(EstimateStatus::Created.as_str(), "CREATED");
        assert_eq!(
            EstimateStatus::from_str_or_created("EXPIRED"),
            EstimateStatus::Expired
        );
    }

    #[test]
    fn ルート候補は属性を保持する() {
        let c = RouteCandidate::new(
            "V001",
            vec!["SGSIN".to_string()],
            14,
            Decimal::new(120000, 0),
            1,
        );
        assert_eq!(c.voyage_number(), "V001");
        assert_eq!(c.transit_days(), 14);
        assert_eq!(c.rank(), 1);
        assert_eq!(c.transit_ports(), &["SGSIN".to_string()]);
    }
}
