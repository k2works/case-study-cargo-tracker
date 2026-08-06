//! estimation コンテキストのドメイン層。
//!
//! 輸送見積（US01）の集約・値オブジェクト・出力ポートを提供する。BC 独立のため他コンテキストの
//! ドメインクレートには依存しない。ルート候補の算出（Routing 参照）は app 層が ACL 経由で行う。

mod aggregate;
mod error;
mod ports;
mod value_objects;

pub use aggregate::Estimate;
pub use error::EstimationError;
pub use ports::{EstimateRepository, EstimationRepositoryError};
pub use value_objects::{
    CargoType, EstimateId, EstimateLocation, EstimateStatus, RouteCandidate, Weight,
};
