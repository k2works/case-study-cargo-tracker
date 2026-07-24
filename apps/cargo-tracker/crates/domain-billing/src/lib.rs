//! billing コンテキストのドメイン層。
//!
//! 輸送料金の算出（US21）と法人割引の適用（US22）の集約・値オブジェクト・出力ポートを提供する。
//! BC 独立のため他コンテキストのドメインクレートには依存しない。輸送実績（Booking/Handling/Routing）の
//! 参照と割引率（Shipper）の取得は app 層が ACL 経由で行う（ADR-0007 のパターン踏襲）。
//! `Money`・`DiscountRate` は shared-kernel へ昇格せず Billing ローカルに定義する（ADR-0010）。

mod aggregate;
mod error;
mod ports;
mod value_objects;

pub use aggregate::{ChargeAdjustment, DiscountLine, FreightCharge};
pub use error::BillingError;
pub use ports::{BillingRepositoryError, FreightChargeRepository};
pub use value_objects::{
    AdjustmentReason, BillingBookingId, ChargeStatus, Currency, DiscountRate, FreightChargeId,
    Money,
};
