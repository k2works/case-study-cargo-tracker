//! Routing Context のドメインエラー。

/// 航海ドメインのエラー型。
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum RoutingError {
    /// 航海番号が空、または 20 文字を超える場合。
    #[error("voyage number must be 1..=20 chars")]
    InvalidVoyageNumber,
    /// 船名が空、または 100 文字を超える場合。
    #[error("vessel name must be 1..=100 chars")]
    InvalidVesselName,
    /// 運送会社名が空、または 100 文字を超える場合。
    #[error("carrier must be 1..=100 chars")]
    InvalidCarrier,
    /// 運送区間の出発地と到着地が同一の場合。
    #[error("carrier movement departure and arrival locations must differ")]
    SameDepartureAndArrival,
    /// 運送区間の出発日時が到着日時以降の場合。
    #[error("carrier movement departure time must be before arrival time")]
    DepartureNotBeforeArrival,
    /// スケジュールが空（運送区間が 1 つもない）の場合。
    #[error("schedule must have at least one carrier movement")]
    EmptySchedule,
    /// スケジュールの運送区間が時系列順に連続していない場合。
    #[error("carrier movements must be in chronological order")]
    NonChronologicalSchedule,
    /// 未知の貨物種別文字列の場合。
    #[error("unknown cargo type: {0}")]
    UnknownCargoType(String),
    /// 経路候補が空（区間が 1 つもない）の場合。
    #[error("route candidate must have at least one segment")]
    EmptyRoute,
    /// 経路候補の区間が接続していない（前区間の到着地/時刻と次区間が連結しない）場合。
    #[error("route segments must connect (location and chronology)")]
    DisconnectedRoute,
}
