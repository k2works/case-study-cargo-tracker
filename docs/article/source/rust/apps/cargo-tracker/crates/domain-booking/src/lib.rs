//! Booking Context のドメイン層。
//!
//! 貨物予約の集約（`Cargo`）・値オブジェクト・出力ポートを定義する。
//! 危険物申告・温度管理条件の必須性、出発地≠目的地などの不変条件を型と `Cargo::book` で強制する。

pub mod aggregate;
pub mod error;
pub mod notification;
pub mod ports;
pub mod value_objects;

pub use aggregate::{BookCargoCommand, Cargo};
pub use error::BookingError;
pub use notification::{Notification, NotificationPort, NotificationType};
pub use ports::{
    AclError, CargoRepository, RepositoryError, SelectedRouteSummary, SelectedRouteView,
    ShipperExistenceChecker,
};
pub use value_objects::{
    BookingId, BookingStatus, CargoType, Consignee, Description, Dimensions, HazardousDeclaration,
    Quantity, RouteSpecification, TemperatureRequirement, TemperatureUnit, Weight,
};
