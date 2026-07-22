//! Routing コンテキストのドメイン層。
//!
//! 航海スケジュール（Voyage 集約）の登録・更新・検索に関わる
//! 集約・値オブジェクト・出力ポートを定義する。

pub mod aggregate;
pub mod error;
pub mod ports;
pub mod route;
pub mod value_objects;

pub use aggregate::Voyage;
pub use error::RoutingError;
pub use ports::{RepositoryError, VoyageRepository, VoyageSearchCriteria};
pub use route::{RouteCandidate, RouteCandidateCalculator, RouteLeg};
pub use value_objects::{CargoType, Carrier, CarrierMovement, Schedule, VesselName, VoyageNumber};
