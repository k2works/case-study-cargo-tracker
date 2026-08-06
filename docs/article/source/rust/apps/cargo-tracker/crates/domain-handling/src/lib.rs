//! handling コンテキストのドメイン層。
//!
//! 荷役作業（受領・積込・荷降し・引取）の記録を担う集約・値オブジェクト・出力ポートを提供する。
//! BC 独立のため他コンテキストのドメインクレートには依存しない。追跡への反映で用いる
//! 「結果としての輸送状態」は文字列（SCREAMING_SNAKE_CASE）で表現し、`domain-tracking` に依存しない。

mod aggregate;
mod error;
mod ports;
mod value_objects;

pub use aggregate::HandlingActivity;
pub use error::HandlingError;
pub use ports::{HandlingActivityRepository, HandlingRepositoryError};
pub use value_objects::{HandlingLocation, HandlingType, ReceiptConfirmation};
