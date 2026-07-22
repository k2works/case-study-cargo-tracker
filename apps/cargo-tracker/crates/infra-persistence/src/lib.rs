//! Booking / Shipper Context の永続化アダプター（sqlx 実装）。
//!
//! ドメインの出力ポート（`ShipperRepository` 等）を PostgreSQL 上で実装する。
//! IT1 ではランタイムクエリを用い、コンパイル時 `query!` マクロ + `.sqlx` オフライン化は
//! 後続タスク（1.7）で硬化する。

pub mod cargo_repository;
pub mod cargo_spec_provider;
pub mod selected_route_repository;
pub mod shipper_query;
pub mod shipper_repository;
pub mod user_repository;
pub mod voyage_repository;

pub use cargo_repository::{SqlxCargoRepository, SqlxShipperExistenceChecker};
pub use cargo_spec_provider::SqlxCargoSpecProvider;
pub use selected_route_repository::SqlxSelectedRouteRepository;
pub use shipper_query::SqlxShipperQueryAdapter;
pub use shipper_repository::SqlxShipperRepository;
pub use user_repository::{
    AuthError, AuthenticatedUser, SqlxUserRepository, hash_password, verify_password,
};
pub use voyage_repository::SqlxVoyageRepository;

/// sqlx マイグレータ。アプリ起動時・テスト時にスキーマを適用する。
pub static MIGRATOR: sqlx::migrate::Migrator = sqlx::migrate!("./migrations");
