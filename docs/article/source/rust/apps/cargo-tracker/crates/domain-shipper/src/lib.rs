//! Shipper Context のドメイン層。
//!
//! 荷主（個人・法人）の集約・値オブジェクト・出力ポートを定義する。
//! 不変条件は値オブジェクトのスマートコンストラクタと `ShipperKind` enum で型レベルに強制する。

pub mod aggregate;
pub mod error;
pub mod ports;
pub mod value_objects;

pub use aggregate::{CorporateProfile, Shipper, ShipperKind};
pub use error::ShipperError;
pub use ports::{RepositoryError, ShipperRepository};
pub use value_objects::{
    Address, ContractNumber, DiscountRate, Email, Phone, ShipperCode, ShipperName,
};
