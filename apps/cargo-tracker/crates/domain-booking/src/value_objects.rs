//! Booking Context の値オブジェクト。

use crate::error::BookingError;
use chrono::NaiveDate;
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use shared_kernel::Location;
use uuid::Uuid;

/// 予約 ID（業務キー。`BKG-` プレフィックス + UUID 先頭 8 文字）。
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct BookingId(String);

impl BookingId {
    /// 新しい予約 ID を採番する。
    #[must_use]
    pub fn generate() -> Self {
        let uuid = Uuid::new_v4().simple().to_string();
        Self(format!("BKG-{}", &uuid[..8].to_uppercase()))
    }

    /// 既存文字列から予約 ID を構成する。
    ///
    /// # Errors
    ///
    /// 空文字列の場合は `BookingError::InvalidBookingId` を返す。
    pub fn parse(value: impl Into<String>) -> Result<Self, BookingError> {
        let value = value.into();
        if value.trim().is_empty() {
            return Err(BookingError::InvalidBookingId);
        }
        Ok(Self(value))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 予約状態（9 段階の予約ライフサイクル）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum BookingStatus {
    /// 仮受付。
    Preliminary,
    /// 経路設計中（経路設計依頼済み・設計者が経路を設計している）。
    RouteDesigning,
    /// 経路提案中。
    RouteProposed,
    /// 予約確定。
    Confirmed,
    /// 追跡番号発行済。
    TrackingIssued,
    /// 輸送中。
    InTransit,
    /// 配送完了。
    Delivered,
    /// 精算済。
    Settled,
    /// キャンセル済。
    Cancelled,
}

impl BookingStatus {
    /// 文字列表現を返す（永続化用。SCREAMING_SNAKE_CASE）。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Preliminary => "PRELIMINARY",
            Self::RouteDesigning => "ROUTE_DESIGNING",
            Self::RouteProposed => "ROUTE_PROPOSED",
            Self::Confirmed => "CONFIRMED",
            Self::TrackingIssued => "TRACKING_ISSUED",
            Self::InTransit => "IN_TRANSIT",
            Self::Delivered => "DELIVERED",
            Self::Settled => "SETTLED",
            Self::Cancelled => "CANCELLED",
        }
    }

    /// 文字列表現から予約状態を復元する。未知の値は `Preliminary` にフォールバックする。
    #[must_use]
    pub fn from_str_or_preliminary(value: &str) -> Self {
        match value {
            "ROUTE_DESIGNING" => Self::RouteDesigning,
            "ROUTE_PROPOSED" => Self::RouteProposed,
            "CONFIRMED" => Self::Confirmed,
            "TRACKING_ISSUED" => Self::TrackingIssued,
            "IN_TRANSIT" => Self::InTransit,
            "DELIVERED" => Self::Delivered,
            "SETTLED" => Self::Settled,
            "CANCELLED" => Self::Cancelled,
            _ => Self::Preliminary,
        }
    }
}

/// 貨物種別。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum CargoType {
    /// 一般貨物。
    General,
    /// 危険物。
    Hazardous,
    /// 冷凍・冷蔵貨物。
    Refrigerated,
}

impl CargoType {
    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::General => "GENERAL",
            Self::Hazardous => "HAZARDOUS",
            Self::Refrigerated => "REFRIGERATED",
        }
    }

    /// 文字列表現から貨物種別を復元する。未知の値は `General` にフォールバックする。
    #[must_use]
    pub fn from_str_or_general(value: &str) -> Self {
        match value {
            "HAZARDOUS" => Self::Hazardous,
            "REFRIGERATED" => Self::Refrigerated,
            _ => Self::General,
        }
    }
}

/// ルート仕様（出発地・目的地・到着期限）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RouteSpecification {
    origin: Location,
    destination: Location,
    arrival_deadline: NaiveDate,
}

impl RouteSpecification {
    /// ルート仕様を生成する。
    ///
    /// # Errors
    ///
    /// 出発地と目的地が同一の場合は `BookingError::SameOriginAndDestination` を返す。
    pub fn new(
        origin: Location,
        destination: Location,
        arrival_deadline: NaiveDate,
    ) -> Result<Self, BookingError> {
        if origin == destination {
            return Err(BookingError::SameOriginAndDestination);
        }
        Ok(Self {
            origin,
            destination,
            arrival_deadline,
        })
    }

    /// 出発地を返す。
    #[must_use]
    pub fn origin(&self) -> &Location {
        &self.origin
    }

    /// 目的地を返す。
    #[must_use]
    pub fn destination(&self) -> &Location {
        &self.destination
    }

    /// 到着期限を返す。
    #[must_use]
    pub fn arrival_deadline(&self) -> NaiveDate {
        self.arrival_deadline
    }
}

/// 重量（kg、0 より大きい）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Weight(Decimal);

impl Weight {
    /// 重量を生成する。
    ///
    /// # Errors
    ///
    /// 0 以下の場合は `BookingError::InvalidWeight` を返す。
    pub fn new(value: Decimal) -> Result<Self, BookingError> {
        if value <= Decimal::ZERO {
            return Err(BookingError::InvalidWeight);
        }
        Ok(Self(value))
    }

    /// 内部の `Decimal` 値を返す。
    #[must_use]
    pub fn value(&self) -> Decimal {
        self.0
    }
}

/// 荷受人情報。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Consignee {
    name: String,
    contact: String,
}

impl Consignee {
    /// 荷受人情報を生成する。
    ///
    /// # Errors
    ///
    /// 名称または連絡先が空の場合は `BookingError::InvalidConsignee` を返す。
    pub fn new(name: impl Into<String>, contact: impl Into<String>) -> Result<Self, BookingError> {
        let name = name.into().trim().to_string();
        let contact = contact.into().trim().to_string();
        if name.is_empty() {
            return Err(BookingError::InvalidConsignee("name is empty".to_string()));
        }
        if contact.is_empty() {
            return Err(BookingError::InvalidConsignee(
                "contact is empty".to_string(),
            ));
        }
        Ok(Self { name, contact })
    }

    /// 荷受人名を返す。
    #[must_use]
    pub fn name(&self) -> &str {
        &self.name
    }

    /// 荷受人連絡先を返す。
    #[must_use]
    pub fn contact(&self) -> &str {
        &self.contact
    }
}

/// 危険物申告（HAZARDOUS 時に必須）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct HazardousDeclaration {
    hazardous_class: String,
    un_number: String,
    proper_shipping_name: String,
}

impl HazardousDeclaration {
    /// 危険物申告を生成する。
    ///
    /// # Errors
    ///
    /// いずれかの項目が空の場合は `BookingError::InvalidHazardousDeclaration` を返す。
    pub fn new(
        hazardous_class: impl Into<String>,
        un_number: impl Into<String>,
        proper_shipping_name: impl Into<String>,
    ) -> Result<Self, BookingError> {
        let hazardous_class = hazardous_class.into().trim().to_string();
        let un_number = un_number.into().trim().to_string();
        let proper_shipping_name = proper_shipping_name.into().trim().to_string();
        if hazardous_class.is_empty() || un_number.is_empty() || proper_shipping_name.is_empty() {
            return Err(BookingError::InvalidHazardousDeclaration);
        }
        Ok(Self {
            hazardous_class,
            un_number,
            proper_shipping_name,
        })
    }

    /// 危険物クラスを返す。
    #[must_use]
    pub fn hazardous_class(&self) -> &str {
        &self.hazardous_class
    }

    /// UN 番号を返す。
    #[must_use]
    pub fn un_number(&self) -> &str {
        &self.un_number
    }

    /// 正式輸送品名を返す。
    #[must_use]
    pub fn proper_shipping_name(&self) -> &str {
        &self.proper_shipping_name
    }
}

/// 温度単位。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TemperatureUnit {
    /// 摂氏。
    Celsius,
    /// 華氏。
    Fahrenheit,
}

impl TemperatureUnit {
    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Celsius => "CELSIUS",
            Self::Fahrenheit => "FAHRENHEIT",
        }
    }

    /// 文字列表現から温度単位を復元する。未知の値は `Celsius` にフォールバックする。
    #[must_use]
    pub fn from_str_or_celsius(value: &str) -> Self {
        match value {
            "FAHRENHEIT" => Self::Fahrenheit,
            _ => Self::Celsius,
        }
    }
}

/// 温度管理条件（REFRIGERATED 時に必須）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct TemperatureRequirement {
    min_temperature: Decimal,
    max_temperature: Decimal,
    unit: TemperatureUnit,
}

impl TemperatureRequirement {
    /// 温度管理条件を生成する。
    ///
    /// # Errors
    ///
    /// 最低温度が最高温度を上回る場合は `BookingError::InvalidTemperatureRequirement` を返す。
    pub fn new(
        min_temperature: Decimal,
        max_temperature: Decimal,
        unit: TemperatureUnit,
    ) -> Result<Self, BookingError> {
        if min_temperature > max_temperature {
            return Err(BookingError::InvalidTemperatureRequirement);
        }
        Ok(Self {
            min_temperature,
            max_temperature,
            unit,
        })
    }

    /// 最低温度を返す。
    #[must_use]
    pub fn min_temperature(&self) -> Decimal {
        self.min_temperature
    }

    /// 最高温度を返す。
    #[must_use]
    pub fn max_temperature(&self) -> Decimal {
        self.max_temperature
    }

    /// 温度単位を返す。
    #[must_use]
    pub fn unit(&self) -> TemperatureUnit {
        self.unit
    }
}

/// 貨物寸法（任意項目）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Dimensions {
    /// 長さ（cm）。
    pub length: Decimal,
    /// 幅（cm）。
    pub width: Decimal,
    /// 高さ（cm）。
    pub height: Decimal,
}

/// 貨物個数（1 以上、任意項目）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Quantity(u32);

impl Quantity {
    /// 個数を生成する。
    ///
    /// # Errors
    ///
    /// 0 の場合は `BookingError::InvalidQuantity` を返す。
    pub fn new(value: u32) -> Result<Self, BookingError> {
        if value == 0 {
            return Err(BookingError::InvalidQuantity);
        }
        Ok(Self(value))
    }

    /// 個数を返す。
    #[must_use]
    pub fn value(&self) -> u32 {
        self.0
    }
}

/// 品名（任意項目、最大 500 文字）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Description(String);

impl Description {
    /// 品名を生成する。
    ///
    /// # Errors
    ///
    /// 500 文字を超える場合は `BookingError::InvalidDescription` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, BookingError> {
        let value = value.into().trim().to_string();
        if value.chars().count() > 500 {
            return Err(BookingError::InvalidDescription);
        }
        Ok(Self(value))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn loc(code: &str) -> Location {
        Location::new(code).unwrap()
    }

    #[test]
    fn 予約_id_は_bkg_プレフィックスで生成される() {
        let id = BookingId::generate();
        assert!(id.as_str().starts_with("BKG-"));
    }

    #[test]
    fn ルート仕様は出発地と目的地が同一だとエラー() {
        let deadline = NaiveDate::from_ymd_opt(2026, 4, 15).unwrap();
        assert_eq!(
            RouteSpecification::new(loc("JPOSA"), loc("JPOSA"), deadline),
            Err(BookingError::SameOriginAndDestination)
        );
        assert!(RouteSpecification::new(loc("JPOSA"), loc("USLAX"), deadline).is_ok());
    }

    #[test]
    fn 重量は0以下だとエラー() {
        assert!(Weight::new(Decimal::new(12, 1)).is_ok());
        assert_eq!(Weight::new(Decimal::ZERO), Err(BookingError::InvalidWeight));
    }

    #[test]
    fn 荷受人は名称と連絡先が必須() {
        assert!(Consignee::new("LA Trading", "contact@la.example").is_ok());
        assert!(Consignee::new("", "x").is_err());
        assert!(Consignee::new("name", "  ").is_err());
    }

    #[test]
    fn 温度管理条件は最低が最高を超えるとエラー() {
        assert_eq!(
            TemperatureRequirement::new(
                Decimal::new(5, 0),
                Decimal::new(1, 0),
                TemperatureUnit::Celsius
            ),
            Err(BookingError::InvalidTemperatureRequirement)
        );
        assert!(
            TemperatureRequirement::new(
                Decimal::new(-20, 0),
                Decimal::new(-5, 0),
                TemperatureUnit::Celsius
            )
            .is_ok()
        );
    }
}
