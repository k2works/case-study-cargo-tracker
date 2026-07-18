//! Booking / Shipper Context の永続化アダプター（sqlx 実装）。
//!
//! ドメインの出力ポート（`ShipperRepository` 等）を PostgreSQL 上で実装する。
//! IT1 ではランタイムクエリを用い、コンパイル時 `query!` マクロ + `.sqlx` オフライン化は
//! 後続タスク（1.7）で硬化する。

pub mod shipper_repository;

pub use shipper_repository::SqlxShipperRepository;

/// sqlx マイグレータ。アプリ起動時・テスト時にスキーマを適用する。
pub static MIGRATOR: sqlx::migrate::Migrator = sqlx::migrate!("./migrations");
