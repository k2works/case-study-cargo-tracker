//! Routing Context の値オブジェクト。

use crate::error::RoutingError;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use shared_kernel::Location;

/// 航海番号（Routing Context 固有の一意識別子。1〜20 文字）。
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct VoyageNumber(String);

impl VoyageNumber {
    /// 航海番号を生成する。
    ///
    /// # Errors
    ///
    /// 空、または 20 文字を超える場合は `RoutingError::InvalidVoyageNumber` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, RoutingError> {
        let value = value.into();
        let trimmed = value.trim();
        if trimmed.is_empty() || trimmed.chars().count() > 20 {
            return Err(RoutingError::InvalidVoyageNumber);
        }
        Ok(Self(trimmed.to_string()))
    }

    /// 永続化からの復元用（検証済みの値を無検査で構成する）。
    #[must_use]
    pub fn from_string(value: String) -> Self {
        Self(value)
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 船名（1〜100 文字）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct VesselName(String);

impl VesselName {
    /// 船名を生成する。
    ///
    /// # Errors
    ///
    /// 空、または 100 文字を超える場合は `RoutingError::InvalidVesselName` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, RoutingError> {
        let value = value.into();
        let trimmed = value.trim();
        if trimmed.is_empty() || trimmed.chars().count() > 100 {
            return Err(RoutingError::InvalidVesselName);
        }
        Ok(Self(trimmed.to_string()))
    }

    /// 永続化からの復元用。
    #[must_use]
    pub fn from_string(value: String) -> Self {
        Self(value)
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 運送会社名（1〜100 文字）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Carrier(String);

impl Carrier {
    /// 運送会社名を生成する。
    ///
    /// # Errors
    ///
    /// 空、または 100 文字を超える場合は `RoutingError::InvalidCarrier` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, RoutingError> {
        let value = value.into();
        let trimmed = value.trim();
        if trimmed.is_empty() || trimmed.chars().count() > 100 {
            return Err(RoutingError::InvalidCarrier);
        }
        Ok(Self(trimmed.to_string()))
    }

    /// 永続化からの復元用。
    #[must_use]
    pub fn from_string(value: String) -> Self {
        Self(value)
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 航海が対応する貨物種別（Routing Context 固有型）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum CargoType {
    /// 一般貨物。
    General,
    /// 危険物。
    Hazardous,
    /// 冷凍・冷蔵貨物。
    Refrigerated,
}

impl CargoType {
    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::General => "GENERAL",
            Self::Hazardous => "HAZARDOUS",
            Self::Refrigerated => "REFRIGERATED",
        }
    }

    /// 文字列から貨物種別をパースする。
    ///
    /// # Errors
    ///
    /// 未知の貨物種別文字列の場合は `RoutingError::UnknownCargoType` を返す。
    pub fn parse(value: &str) -> Result<Self, RoutingError> {
        match value {
            "GENERAL" => Ok(Self::General),
            "HAZARDOUS" => Ok(Self::Hazardous),
            "REFRIGERATED" => Ok(Self::Refrigerated),
            other => Err(RoutingError::UnknownCargoType(other.to_string())),
        }
    }
}

/// 運送区間（エンティティ）。単一航海における出発地から到着地までの移動を表す。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CarrierMovement {
    departure_location: Location,
    arrival_location: Location,
    departure_time: DateTime<Utc>,
    arrival_time: DateTime<Utc>,
}

impl CarrierMovement {
    /// 運送区間を生成する。
    ///
    /// # Errors
    ///
    /// - 出発地と到着地が同一の場合は `RoutingError::SameDepartureAndArrival`
    /// - 出発日時が到着日時以降の場合は `RoutingError::DepartureNotBeforeArrival`
    pub fn new(
        departure_location: Location,
        arrival_location: Location,
        departure_time: DateTime<Utc>,
        arrival_time: DateTime<Utc>,
    ) -> Result<Self, RoutingError> {
        if departure_location == arrival_location {
            return Err(RoutingError::SameDepartureAndArrival);
        }
        if departure_time >= arrival_time {
            return Err(RoutingError::DepartureNotBeforeArrival);
        }
        Ok(Self {
            departure_location,
            arrival_location,
            departure_time,
            arrival_time,
        })
    }

    /// 出発地を返す。
    #[must_use]
    pub fn departure_location(&self) -> &Location {
        &self.departure_location
    }

    /// 到着地を返す。
    #[must_use]
    pub fn arrival_location(&self) -> &Location {
        &self.arrival_location
    }

    /// 出発日時を返す。
    #[must_use]
    pub fn departure_time(&self) -> DateTime<Utc> {
        self.departure_time
    }

    /// 到着日時を返す。
    #[must_use]
    pub fn arrival_time(&self) -> DateTime<Utc> {
        self.arrival_time
    }
}

/// 航海スケジュール（時系列順の運送区間一覧を保持する値オブジェクト）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Schedule {
    carrier_movements: Vec<CarrierMovement>,
}

impl Schedule {
    /// 運送区間の一覧からスケジュールを生成する。
    ///
    /// # Errors
    ///
    /// - 空の場合は `RoutingError::EmptySchedule`
    /// - 前の区間の到着日時が次の区間の出発日時より後の場合は `RoutingError::NonChronologicalSchedule`
    pub fn new(carrier_movements: Vec<CarrierMovement>) -> Result<Self, RoutingError> {
        if carrier_movements.is_empty() {
            return Err(RoutingError::EmptySchedule);
        }
        for pair in carrier_movements.windows(2) {
            if pair[0].arrival_time() > pair[1].departure_time() {
                return Err(RoutingError::NonChronologicalSchedule);
            }
        }
        Ok(Self { carrier_movements })
    }

    /// 運送区間の一覧を返す。
    #[must_use]
    pub fn carrier_movements(&self) -> &[CarrierMovement] {
        &self.carrier_movements
    }

    /// スケジュール全体の出発地（最初の運送区間の出発地）を返す。
    #[must_use]
    pub fn origin(&self) -> &Location {
        self.carrier_movements[0].departure_location()
    }

    /// スケジュール全体の到着地（最後の運送区間の到着地）を返す。
    #[must_use]
    pub fn destination(&self) -> &Location {
        self.carrier_movements[self.carrier_movements.len() - 1].arrival_location()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn loc(code: &str) -> Location {
        Location::new(code).expect("有効な UN/LOCODE")
    }

    fn dt(s: &str) -> DateTime<Utc> {
        DateTime::parse_from_rfc3339(s)
            .expect("有効な日時")
            .with_timezone(&Utc)
    }

    #[test]
    fn 航海番号は1文字から20文字で生成できる() {
        assert!(VoyageNumber::new("V0042").is_ok());
        assert_eq!(VoyageNumber::new("V0042").unwrap().as_str(), "V0042");
    }

    #[test]
    fn 空の航海番号はエラーになる() {
        assert_eq!(
            VoyageNumber::new("  "),
            Err(RoutingError::InvalidVoyageNumber)
        );
    }

    #[test]
    fn 二十文字を超える航海番号はエラーになる() {
        assert_eq!(
            VoyageNumber::new("V".repeat(21)),
            Err(RoutingError::InvalidVoyageNumber)
        );
    }

    #[test]
    fn 船名と運送会社は空だとエラーになる() {
        assert_eq!(VesselName::new(""), Err(RoutingError::InvalidVesselName));
        assert_eq!(Carrier::new(""), Err(RoutingError::InvalidCarrier));
    }

    #[test]
    fn 貨物種別は文字列と相互変換できる() {
        assert_eq!(CargoType::parse("HAZARDOUS").unwrap(), CargoType::Hazardous);
        assert_eq!(CargoType::Refrigerated.as_str(), "REFRIGERATED");
        assert!(CargoType::parse("UNKNOWN").is_err());
    }

    #[test]
    fn 運送区間は出発地と到着地が異なり出発が到着より前なら生成できる() {
        let movement = CarrierMovement::new(
            loc("JPOSA"),
            loc("USLAX"),
            dt("2026-04-01T18:00:00Z"),
            dt("2026-04-14T08:00:00Z"),
        );
        assert!(movement.is_ok());
    }

    #[test]
    fn 出発地と到着地が同一の運送区間はエラーになる() {
        let movement = CarrierMovement::new(
            loc("JPOSA"),
            loc("JPOSA"),
            dt("2026-04-01T18:00:00Z"),
            dt("2026-04-14T08:00:00Z"),
        );
        assert_eq!(movement, Err(RoutingError::SameDepartureAndArrival));
    }

    #[test]
    fn 出発が到着以降の運送区間はエラーになる() {
        let movement = CarrierMovement::new(
            loc("JPOSA"),
            loc("USLAX"),
            dt("2026-04-14T08:00:00Z"),
            dt("2026-04-01T18:00:00Z"),
        );
        assert_eq!(movement, Err(RoutingError::DepartureNotBeforeArrival));
    }

    #[test]
    fn スケジュールは時系列順の運送区間で生成できる() {
        let leg1 = CarrierMovement::new(
            loc("JPOSA"),
            loc("SGSIN"),
            dt("2026-04-01T18:00:00Z"),
            dt("2026-04-07T08:00:00Z"),
        )
        .unwrap();
        let leg2 = CarrierMovement::new(
            loc("SGSIN"),
            loc("USLAX"),
            dt("2026-04-08T10:00:00Z"),
            dt("2026-04-20T08:00:00Z"),
        )
        .unwrap();
        let schedule = Schedule::new(vec![leg1, leg2]).unwrap();
        assert_eq!(schedule.origin().code(), "JPOSA");
        assert_eq!(schedule.destination().code(), "USLAX");
    }

    #[test]
    fn 空のスケジュールはエラーになる() {
        assert_eq!(Schedule::new(vec![]), Err(RoutingError::EmptySchedule));
    }

    #[test]
    fn 時系列が逆転したスケジュールはエラーになる() {
        let leg1 = CarrierMovement::new(
            loc("JPOSA"),
            loc("SGSIN"),
            dt("2026-04-10T18:00:00Z"),
            dt("2026-04-20T08:00:00Z"),
        )
        .unwrap();
        let leg2 = CarrierMovement::new(
            loc("SGSIN"),
            loc("USLAX"),
            dt("2026-04-08T10:00:00Z"),
            dt("2026-04-15T08:00:00Z"),
        )
        .unwrap();
        assert_eq!(
            Schedule::new(vec![leg1, leg2]),
            Err(RoutingError::NonChronologicalSchedule)
        );
    }
}
