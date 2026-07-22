//! Routing Context の集約ルート。

use crate::value_objects::{CargoType, Carrier, Schedule, VesselName, VoyageNumber};
use shared_kernel::Location;

/// 航海（集約ルート）。航海番号・船名・運送会社・対応貨物種別・スケジュールを管理する。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Voyage {
    voyage_number: VoyageNumber,
    vessel_name: VesselName,
    carrier: Carrier,
    supported_cargo_types: Vec<CargoType>,
    schedule: Schedule,
}

impl Voyage {
    /// 新しい航海を登録する。
    ///
    /// 対応貨物種別は空の場合、一般貨物（`General`）のみを対応とみなす。
    #[must_use]
    pub fn register(
        voyage_number: VoyageNumber,
        vessel_name: VesselName,
        carrier: Carrier,
        supported_cargo_types: Vec<CargoType>,
        schedule: Schedule,
    ) -> Self {
        let supported_cargo_types = if supported_cargo_types.is_empty() {
            vec![CargoType::General]
        } else {
            supported_cargo_types
        };
        Self {
            voyage_number,
            vessel_name,
            carrier,
            supported_cargo_types,
            schedule,
        }
    }

    /// 永続化からの復元用（検証済みの構成要素から集約を組み立てる）。
    #[must_use]
    pub fn reconstitute(
        voyage_number: VoyageNumber,
        vessel_name: VesselName,
        carrier: Carrier,
        supported_cargo_types: Vec<CargoType>,
        schedule: Schedule,
    ) -> Self {
        Self {
            voyage_number,
            vessel_name,
            carrier,
            supported_cargo_types,
            schedule,
        }
    }

    /// 航海スケジュール・船名・運送会社・対応貨物種別を最新情報で上書き更新する（US25）。
    pub fn update(
        &mut self,
        vessel_name: VesselName,
        carrier: Carrier,
        supported_cargo_types: Vec<CargoType>,
        schedule: Schedule,
    ) {
        self.vessel_name = vessel_name;
        self.carrier = carrier;
        self.supported_cargo_types = if supported_cargo_types.is_empty() {
            vec![CargoType::General]
        } else {
            supported_cargo_types
        };
        self.schedule = schedule;
    }

    /// 航海番号を返す。
    #[must_use]
    pub fn voyage_number(&self) -> &VoyageNumber {
        &self.voyage_number
    }

    /// 船名を返す。
    #[must_use]
    pub fn vessel_name(&self) -> &VesselName {
        &self.vessel_name
    }

    /// 運送会社を返す。
    #[must_use]
    pub fn carrier(&self) -> &Carrier {
        &self.carrier
    }

    /// 対応貨物種別の一覧を返す。
    #[must_use]
    pub fn supported_cargo_types(&self) -> &[CargoType] {
        &self.supported_cargo_types
    }

    /// スケジュールを返す。
    #[must_use]
    pub fn schedule(&self) -> &Schedule {
        &self.schedule
    }

    /// スケジュール全体の出発地を返す。
    #[must_use]
    pub fn origin(&self) -> &Location {
        self.schedule.origin()
    }

    /// スケジュール全体の到着地を返す。
    #[must_use]
    pub fn destination(&self) -> &Location {
        self.schedule.destination()
    }

    /// 指定した貨物種別に対応しているかを返す（US07 の絞り込みに使用）。
    #[must_use]
    pub fn supports(&self, cargo_type: CargoType) -> bool {
        self.supported_cargo_types.contains(&cargo_type)
    }

    /// この航海が指定条件を満たすかを判定する（US07 検索の制約評価）。
    ///
    /// 出発地・到着地・貨物種別のいずれも `None` の条件は「指定なし」として素通しする。
    #[must_use]
    pub fn matches(
        &self,
        origin: Option<&Location>,
        destination: Option<&Location>,
        cargo_type: Option<CargoType>,
    ) -> bool {
        if origin.is_some_and(|o| self.origin() != o) {
            return false;
        }
        if destination.is_some_and(|d| self.destination() != d) {
            return false;
        }
        if cargo_type.is_some_and(|ct| !self.supports(ct)) {
            return false;
        }
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::value_objects::CarrierMovement;
    use chrono::{DateTime, Utc};

    fn loc(code: &str) -> Location {
        Location::new(code).expect("有効な UN/LOCODE")
    }

    fn dt(s: &str) -> DateTime<Utc> {
        DateTime::parse_from_rfc3339(s)
            .expect("有効な日時")
            .with_timezone(&Utc)
    }

    fn sample_schedule() -> Schedule {
        let leg = CarrierMovement::new(
            loc("JPOSA"),
            loc("USLAX"),
            dt("2026-04-01T18:00:00Z"),
            dt("2026-04-14T08:00:00Z"),
        )
        .unwrap();
        Schedule::new(vec![leg]).unwrap()
    }

    fn sample_voyage(cargo_types: Vec<CargoType>) -> Voyage {
        Voyage::register(
            VoyageNumber::new("V0042").unwrap(),
            VesselName::new("SAKURA MARU").unwrap(),
            Carrier::new("Nippon Line").unwrap(),
            cargo_types,
            sample_schedule(),
        )
    }

    #[test]
    fn 航海を登録すると航海番号と出発地到着地が参照できる() {
        let voyage = sample_voyage(vec![CargoType::General]);
        assert_eq!(voyage.voyage_number().as_str(), "V0042");
        assert_eq!(voyage.origin().code(), "JPOSA");
        assert_eq!(voyage.destination().code(), "USLAX");
    }

    #[test]
    fn 対応貨物種別が未指定なら一般貨物のみ対応とみなす() {
        let voyage = sample_voyage(vec![]);
        assert!(voyage.supports(CargoType::General));
        assert!(!voyage.supports(CargoType::Hazardous));
    }

    #[test]
    fn 危険物対応の航海は危険物を支持する() {
        let voyage = sample_voyage(vec![CargoType::General, CargoType::Hazardous]);
        assert!(voyage.supports(CargoType::Hazardous));
        assert!(!voyage.supports(CargoType::Refrigerated));
    }

    #[test]
    fn 更新でスケジュールと属性が上書きされる() {
        let mut voyage = sample_voyage(vec![CargoType::General]);
        let new_leg = CarrierMovement::new(
            loc("JPYOK"),
            loc("DEHAM"),
            dt("2026-05-01T18:00:00Z"),
            dt("2026-05-20T08:00:00Z"),
        )
        .unwrap();
        voyage.update(
            VesselName::new("FUJI MARU").unwrap(),
            Carrier::new("Ocean Corp").unwrap(),
            vec![CargoType::Refrigerated],
            Schedule::new(vec![new_leg]).unwrap(),
        );
        assert_eq!(voyage.vessel_name().as_str(), "FUJI MARU");
        assert_eq!(voyage.origin().code(), "JPYOK");
        assert!(voyage.supports(CargoType::Refrigerated));
    }

    #[test]
    fn 検索条件に一致する航海を判定できる() {
        let voyage = sample_voyage(vec![CargoType::General, CargoType::Hazardous]);
        assert!(voyage.matches(
            Some(&loc("JPOSA")),
            Some(&loc("USLAX")),
            Some(CargoType::Hazardous)
        ));
        assert!(voyage.matches(None, None, None));
        assert!(!voyage.matches(Some(&loc("JPYOK")), None, None));
        assert!(!voyage.matches(None, None, Some(CargoType::Refrigerated)));
    }
}
