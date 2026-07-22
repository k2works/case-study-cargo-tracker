//! 経路探索（US08）のドメイン: `RouteLeg`・`RouteCandidate`・`RouteCandidateCalculator`。
//!
//! 登録済み航海（`Voyage`）から、出発地・目的地・期限・貨物種別の制約を満たす
//! 経路候補を算出する。BC 独立性のため Booking Context の `Leg` は共有せず、
//! Routing 固有の `RouteLeg` を用いる（IT2 の `CargoType` 固有型化と一貫）。

use crate::aggregate::Voyage;
use crate::error::RoutingError;
use crate::value_objects::{CargoType, VoyageNumber};
use chrono::{DateTime, NaiveDate, Utc};
use shared_kernel::Location;

/// 経路の 1 区間。単一航海を出発地から到着地まで乗り継ぐ移動を表す（Routing 固有）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RouteLeg {
    voyage: VoyageNumber,
    load_location: Location,
    unload_location: Location,
    load_time: DateTime<Utc>,
    unload_time: DateTime<Utc>,
}

impl RouteLeg {
    /// 航海の端から端まで（出発地 → 到着地）を 1 区間として構成する。
    #[must_use]
    pub fn from_voyage(voyage: &Voyage) -> Self {
        let schedule = voyage.schedule();
        let movements = schedule.carrier_movements();
        let first = &movements[0];
        let last = &movements[movements.len() - 1];
        Self {
            voyage: voyage.voyage_number().clone(),
            load_location: schedule.origin().clone(),
            unload_location: schedule.destination().clone(),
            load_time: first.departure_time(),
            unload_time: last.arrival_time(),
        }
    }

    /// 航海番号を返す。
    #[must_use]
    pub fn voyage(&self) -> &VoyageNumber {
        &self.voyage
    }

    /// 積込地を返す。
    #[must_use]
    pub fn load_location(&self) -> &Location {
        &self.load_location
    }

    /// 荷降地を返す。
    #[must_use]
    pub fn unload_location(&self) -> &Location {
        &self.unload_location
    }

    /// 積込日時を返す。
    #[must_use]
    pub fn load_time(&self) -> DateTime<Utc> {
        self.load_time
    }

    /// 荷降日時を返す。
    #[must_use]
    pub fn unload_time(&self) -> DateTime<Utc> {
        self.unload_time
    }
}

/// 経路候補（1 つ以上の連結した `RouteLeg` からなる旅程）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RouteCandidate {
    segments: Vec<RouteLeg>,
}

impl RouteCandidate {
    /// 区間列から経路候補を構成する。
    ///
    /// # Errors
    ///
    /// - 空の場合は `RoutingError::EmptyRoute`
    /// - 前区間の荷降地/時刻と次区間の積込地/時刻が連結しない場合は `RoutingError::DisconnectedRoute`
    pub fn new(segments: Vec<RouteLeg>) -> Result<Self, RoutingError> {
        if segments.is_empty() {
            return Err(RoutingError::EmptyRoute);
        }
        for pair in segments.windows(2) {
            let connected = pair[0].unload_location() == pair[1].load_location()
                && pair[0].unload_time() <= pair[1].load_time();
            if !connected {
                return Err(RoutingError::DisconnectedRoute);
            }
        }
        Ok(Self { segments })
    }

    /// 区間列を返す。
    #[must_use]
    pub fn segments(&self) -> &[RouteLeg] {
        &self.segments
    }

    /// 全体の出発地（先頭区間の積込地）。
    #[must_use]
    pub fn origin(&self) -> &Location {
        self.segments[0].load_location()
    }

    /// 全体の目的地（末尾区間の荷降地）。
    #[must_use]
    pub fn destination(&self) -> &Location {
        self.segments[self.segments.len() - 1].unload_location()
    }

    /// 経由港（先頭の積込地・末尾の荷降地を除く中間の乗り継ぎ港）。
    #[must_use]
    pub fn transit_ports(&self) -> Vec<Location> {
        self.segments
            .iter()
            .skip(1)
            .map(|leg| leg.load_location().clone())
            .collect()
    }

    /// 使用する航海番号の列。
    #[must_use]
    pub fn voyage_numbers(&self) -> Vec<VoyageNumber> {
        self.segments
            .iter()
            .map(|leg| leg.voyage().clone())
            .collect()
    }

    /// 出発日時（先頭区間の積込日時）。
    #[must_use]
    pub fn departure_time(&self) -> DateTime<Utc> {
        self.segments[0].load_time()
    }

    /// 到着予定（末尾区間の荷降日時）。
    #[must_use]
    pub fn expected_arrival(&self) -> DateTime<Utc> {
        self.segments[self.segments.len() - 1].unload_time()
    }

    /// 所要日数（出発日と到着日のカレンダー日数差）。
    #[must_use]
    pub fn transit_days(&self) -> i64 {
        (self.expected_arrival().date_naive() - self.departure_time().date_naive()).num_days()
    }

    /// 区間数（直行なら 1）。
    #[must_use]
    pub fn leg_count(&self) -> usize {
        self.segments.len()
    }

    /// 到着予定が期限内かを返す。
    #[must_use]
    pub fn within_deadline(&self, deadline: NaiveDate) -> bool {
        self.expected_arrival().date_naive() <= deadline
    }
}

/// 経路探索ドメインサービス（US08）。登録済み航海から経路候補を算出する。
pub struct RouteCandidateCalculator {
    /// 探索する区間数の上限（直行=1、単純接続=2、多段=3…）。
    max_legs: usize,
}

impl Default for RouteCandidateCalculator {
    fn default() -> Self {
        // 直行 + 単純接続（1 経由）+ 多段接続（2 経由）まで。組み合わせ爆発を防ぐ上限。
        Self { max_legs: 3 }
    }
}

impl RouteCandidateCalculator {
    /// 探索する区間数の上限を指定して生成する。
    #[must_use]
    pub fn with_max_legs(max_legs: usize) -> Self {
        Self { max_legs }
    }

    /// 出発地 `origin` から目的地 `destination` へ、`cargo_type` に対応する航海のみを用いて
    /// 経路候補を算出し、推奨順（区間数が少ない順 → 所要日数が短い順 → 期限内を優先）に並べて返す。
    ///
    /// 期限 `deadline` を超過する候補も返すが、`within_deadline` で判定でき、推奨順では後方に並ぶ。
    /// 直行便が存在する場合、区間数最小のため最優先候補になる。
    #[must_use]
    pub fn calculate(
        &self,
        origin: &Location,
        destination: &Location,
        deadline: NaiveDate,
        cargo_type: CargoType,
        voyages: &[Voyage],
    ) -> Vec<RouteCandidate> {
        // 貨物種別に対応する航海のみを探索対象にする。
        let usable: Vec<RouteLeg> = voyages
            .iter()
            .filter(|v| v.supports(cargo_type))
            .map(RouteLeg::from_voyage)
            .collect();

        let mut candidates = Vec::new();
        // 深さ優先で origin から連結する経路を組み立てる。
        let mut path: Vec<RouteLeg> = Vec::new();
        self.extend(origin, destination, &usable, &mut path, &mut candidates);

        // 推奨順にソート: 区間数少 → 所要日数短 → 期限内優先。
        candidates.sort_by(|a, b| {
            a.leg_count()
                .cmp(&b.leg_count())
                .then(a.transit_days().cmp(&b.transit_days()))
                .then(
                    b.within_deadline(deadline)
                        .cmp(&a.within_deadline(deadline)),
                )
        });
        candidates
    }

    /// `current` 地点から連結可能な区間を再帰的に伸ばし、`destination` に到達したら候補を確定する。
    fn extend(
        &self,
        current: &Location,
        destination: &Location,
        usable: &[RouteLeg],
        path: &mut Vec<RouteLeg>,
        out: &mut Vec<RouteCandidate>,
    ) {
        if path.len() >= self.max_legs {
            return;
        }
        for leg in usable {
            // 現在地から乗れる区間か（同一港・時系列連結）。
            if leg.load_location() != current {
                continue;
            }
            if path
                .last()
                .is_some_and(|prev| prev.unload_time() > leg.load_time())
            {
                continue; // 前区間の到着より前には乗れない
            }
            // 同一航海の再利用（サイクル）を避ける。
            if path.iter().any(|l| l.voyage() == leg.voyage()) {
                continue;
            }
            path.push(leg.clone());
            if leg.unload_location() == destination {
                if let Ok(candidate) = RouteCandidate::new(path.clone()) {
                    out.push(candidate);
                }
            } else {
                let next = leg.unload_location().clone();
                self.extend(&next, destination, usable, path, out);
            }
            path.pop();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::value_objects::{Carrier, CarrierMovement, Schedule, VesselName};

    fn loc(code: &str) -> Location {
        Location::new(code).expect("有効な UN/LOCODE")
    }

    fn dt(s: &str) -> DateTime<Utc> {
        DateTime::parse_from_rfc3339(s)
            .expect("有効な日時")
            .with_timezone(&Utc)
    }

    fn date(s: &str) -> NaiveDate {
        NaiveDate::parse_from_str(s, "%Y-%m-%d").expect("有効な日付")
    }

    /// 出発地→到着地を 1 区間で結ぶ航海を作る。
    fn voyage(
        number: &str,
        from: &str,
        to: &str,
        dep: &str,
        arr: &str,
        types: Vec<CargoType>,
    ) -> Voyage {
        let leg = CarrierMovement::new(loc(from), loc(to), dt(dep), dt(arr)).unwrap();
        Voyage::register(
            VoyageNumber::new(number).unwrap(),
            VesselName::new("SHIP").unwrap(),
            Carrier::new("CARRIER").unwrap(),
            types,
            Schedule::new(vec![leg]).unwrap(),
        )
    }

    #[test]
    fn 直行便を最優先候補として算出する() {
        let voyages = vec![voyage(
            "V0001",
            "JPOSA",
            "USLAX",
            "2026-04-01T18:00:00Z",
            "2026-04-14T08:00:00Z",
            vec![CargoType::General],
        )];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-04-20"),
            CargoType::General,
            &voyages,
        );
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].leg_count(), 1);
        assert_eq!(result[0].transit_days(), 13);
        assert!(result[0].within_deadline(date("2026-04-20")));
        assert_eq!(result[0].transit_ports().len(), 0);
    }

    #[test]
    fn 単純接続の1経由経路を算出し寄港地を評価する() {
        let voyages = vec![
            voyage(
                "VA",
                "JPOSA",
                "SGSIN",
                "2026-04-01T00:00:00Z",
                "2026-04-06T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "VB",
                "SGSIN",
                "USLAX",
                "2026-04-07T00:00:00Z",
                "2026-04-18T00:00:00Z",
                vec![CargoType::General],
            ),
        ];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-04-25"),
            CargoType::General,
            &voyages,
        );
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].leg_count(), 2);
        assert_eq!(
            result[0]
                .transit_ports()
                .iter()
                .map(|l| l.code().to_string())
                .collect::<Vec<_>>(),
            vec!["SGSIN".to_string()]
        );
    }

    #[test]
    fn 接続時刻が逆転する経路は候補にならない() {
        // VB が VA の到着より前に出発するため接続不可
        let voyages = vec![
            voyage(
                "VA",
                "JPOSA",
                "SGSIN",
                "2026-04-10T00:00:00Z",
                "2026-04-16T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "VB",
                "SGSIN",
                "USLAX",
                "2026-04-07T00:00:00Z",
                "2026-04-18T00:00:00Z",
                vec![CargoType::General],
            ),
        ];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-04-25"),
            CargoType::General,
            &voyages,
        );
        assert!(result.is_empty());
    }

    #[test]
    fn 直行便は経由便より推奨順で上位になる() {
        let voyages = vec![
            // 経由（2 区間）
            voyage(
                "VA",
                "JPOSA",
                "SGSIN",
                "2026-04-01T00:00:00Z",
                "2026-04-05T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "VB",
                "SGSIN",
                "USLAX",
                "2026-04-06T00:00:00Z",
                "2026-04-20T00:00:00Z",
                vec![CargoType::General],
            ),
            // 直行（1 区間）
            voyage(
                "VD",
                "JPOSA",
                "USLAX",
                "2026-04-02T00:00:00Z",
                "2026-04-16T00:00:00Z",
                vec![CargoType::General],
            ),
        ];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-04-25"),
            CargoType::General,
            &voyages,
        );
        assert_eq!(result.len(), 2);
        assert_eq!(result[0].leg_count(), 1); // 直行が先頭
        assert_eq!(result[0].voyage_numbers()[0].as_str(), "VD");
    }

    #[test]
    fn 貨物種別に対応しない航海は除外される() {
        let voyages = vec![voyage(
            "V0001",
            "JPOSA",
            "USLAX",
            "2026-04-01T00:00:00Z",
            "2026-04-14T00:00:00Z",
            vec![CargoType::General], // 危険物非対応
        )];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-04-20"),
            CargoType::Hazardous,
            &voyages,
        );
        assert!(result.is_empty());
    }

    #[test]
    fn 期限超過の候補も返るが期限内判定で区別できる() {
        let voyages = vec![voyage(
            "V0001",
            "JPOSA",
            "USLAX",
            "2026-04-01T00:00:00Z",
            "2026-04-25T00:00:00Z",
            vec![CargoType::General],
        )];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-04-20"),
            CargoType::General,
            &voyages,
        );
        assert_eq!(result.len(), 1);
        assert!(!result[0].within_deadline(date("2026-04-20")));
    }

    #[test]
    fn 多段接続の2経由経路を算出する() {
        let voyages = vec![
            voyage(
                "V1",
                "JPOSA",
                "SGSIN",
                "2026-04-01T00:00:00Z",
                "2026-04-05T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "V2",
                "SGSIN",
                "AEJEA",
                "2026-04-06T00:00:00Z",
                "2026-04-12T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "V3",
                "AEJEA",
                "USLAX",
                "2026-04-13T00:00:00Z",
                "2026-04-28T00:00:00Z",
                vec![CargoType::General],
            ),
        ];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-05-01"),
            CargoType::General,
            &voyages,
        );
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].leg_count(), 3);
        assert_eq!(
            result[0]
                .transit_ports()
                .iter()
                .map(|l| l.code().to_string())
                .collect::<Vec<_>>(),
            vec!["SGSIN".to_string(), "AEJEA".to_string()]
        );
    }

    #[test]
    fn 経路が存在しない場合は空を返す() {
        let voyages = vec![voyage(
            "V0001",
            "JPYOK",
            "DEHAM",
            "2026-04-01T00:00:00Z",
            "2026-04-14T00:00:00Z",
            vec![CargoType::General],
        )];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-04-20"),
            CargoType::General,
            &voyages,
        );
        assert!(result.is_empty());
    }

    #[test]
    fn 乗り継ぎ時間ゼロの同時刻接続は成立する() {
        // 前区間到着 == 次区間出発（`unload_time <= load_time` の等号境界）で接続が成立すること。
        let voyages = vec![
            voyage(
                "VA",
                "JPOSA",
                "SGSIN",
                "2026-04-01T00:00:00Z",
                "2026-04-06T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "VB",
                "SGSIN",
                "USLAX",
                "2026-04-06T00:00:00Z",
                "2026-04-18T00:00:00Z",
                vec![CargoType::General],
            ),
        ];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-04-25"),
            CargoType::General,
            &voyages,
        );
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].leg_count(), 2);
    }

    #[test]
    fn 区間数上限を超える経路は打ち切られる() {
        // max_legs=2 のとき、3 区間必要な経路は候補にならない。
        let voyages = vec![
            voyage(
                "V1",
                "JPOSA",
                "SGSIN",
                "2026-04-01T00:00:00Z",
                "2026-04-05T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "V2",
                "SGSIN",
                "AEJEA",
                "2026-04-06T00:00:00Z",
                "2026-04-12T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "V3",
                "AEJEA",
                "USLAX",
                "2026-04-13T00:00:00Z",
                "2026-04-28T00:00:00Z",
                vec![CargoType::General],
            ),
        ];
        let calc = RouteCandidateCalculator::with_max_legs(2);
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-05-01"),
            CargoType::General,
            &voyages,
        );
        assert!(result.is_empty());
    }

    #[test]
    fn 同一港を循環する航海はサイクル回避で無限探索にならない() {
        // JPOSA→SGSIN→JPOSA→… と循環しうる航海群でも、同一航海の再利用を避け停止する。
        let voyages = vec![
            voyage(
                "VA",
                "JPOSA",
                "SGSIN",
                "2026-04-01T00:00:00Z",
                "2026-04-05T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "VB",
                "SGSIN",
                "JPOSA",
                "2026-04-06T00:00:00Z",
                "2026-04-10T00:00:00Z",
                vec![CargoType::General],
            ),
        ];
        let calc = RouteCandidateCalculator::default();
        // USLAX には到達不能なので候補は空だが、無限ループせず停止すること（本テストが完了する）。
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-05-01"),
            CargoType::General,
            &voyages,
        );
        assert!(result.is_empty());
    }

    #[test]
    fn 同一区間数では所要日数が短い候補が上位になる() {
        // 直行 2 便（同一 leg_count）で、所要日数が短い方が推奨順で上位（第 2 キー）。
        let voyages = vec![
            voyage(
                "VSLOW",
                "JPOSA",
                "USLAX",
                "2026-04-01T00:00:00Z",
                "2026-04-20T00:00:00Z",
                vec![CargoType::General],
            ),
            voyage(
                "VFAST",
                "JPOSA",
                "USLAX",
                "2026-04-01T00:00:00Z",
                "2026-04-12T00:00:00Z",
                vec![CargoType::General],
            ),
        ];
        let calc = RouteCandidateCalculator::default();
        let result = calc.calculate(
            &loc("JPOSA"),
            &loc("USLAX"),
            date("2026-04-25"),
            CargoType::General,
            &voyages,
        );
        assert_eq!(result.len(), 2);
        assert_eq!(result[0].voyage_numbers()[0].as_str(), "VFAST");
    }

    #[test]
    fn 到着日と期限が同日なら期限内と判定する() {
        // 到着 2026-04-14。期限が同日・翌日は期限内、前日は期限超過（境界値 == の検証）。
        let candidate = RouteCandidateCalculator::default()
            .calculate(
                &loc("JPOSA"),
                &loc("USLAX"),
                date("2026-04-14"),
                CargoType::General,
                &[voyage(
                    "V0001",
                    "JPOSA",
                    "USLAX",
                    "2026-04-01T18:00:00Z",
                    "2026-04-14T08:00:00Z",
                    vec![CargoType::General],
                )],
            )
            .remove(0);
        assert!(
            candidate.within_deadline(date("2026-04-14")),
            "同日は期限内"
        );
        assert!(
            candidate.within_deadline(date("2026-04-15")),
            "翌日は期限内"
        );
        assert!(
            !candidate.within_deadline(date("2026-04-13")),
            "前日は期限超過"
        );
    }
}
