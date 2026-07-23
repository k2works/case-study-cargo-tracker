//! tracking コンテキストのドメイン層。
//!
//! 貨物追跡（追跡番号発行・荷役反映・状態手動更新）の集約・値オブジェクト・
//! 出力ポートを提供する。BC 独立のため他コンテキストのドメインクレートには依存しない
//! （予約・航海への参照は文字列 ID／`Option<T>` で保持する）。

mod aggregate;
mod error;
mod ports;
mod value_objects;

pub use aggregate::{TrackingActivity, TrackingActivityEvent};
pub use error::TrackingError;
pub use ports::{
    TrackingActivityRepository, TrackingNumberGenerator, TrackingRepositoryError,
    UuidTrackingNumberGenerator,
};
pub use value_objects::{
    TrackingBookingId, TrackingLocation, TrackingNumber, TrackingStatus, TrackingVoyageNumber,
};
