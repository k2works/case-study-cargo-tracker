//! Cargo 集約。

use crate::error::BookingError;
use crate::value_objects::{
    BookingId, BookingStatus, CargoType, Consignee, Description, Dimensions, HazardousDeclaration,
    Quantity, RouteSpecification, TemperatureRequirement, Weight,
};
use serde::{Deserialize, Serialize};
use shared_kernel::ShipperId;

/// 貨物予約コマンド（新規予約登録の入力）。
#[derive(Debug, Clone)]
pub struct BookCargoCommand {
    /// 荷主 ID。
    pub shipper_id: ShipperId,
    /// ルート仕様。
    pub route_specification: RouteSpecification,
    /// 荷受人情報。
    pub consignee: Consignee,
    /// 貨物種別。
    pub cargo_type: CargoType,
    /// 重量。
    pub weight: Weight,
    /// 寸法（任意）。
    pub dimensions: Option<Dimensions>,
    /// 個数（任意）。
    pub quantity: Option<Quantity>,
    /// 品名（任意）。
    pub description: Option<Description>,
    /// 危険物申告（HAZARDOUS 時に必須）。
    pub hazardous_declaration: Option<HazardousDeclaration>,
    /// 温度管理条件（REFRIGERATED 時に必須）。
    pub temperature_requirement: Option<TemperatureRequirement>,
}

/// 貨物集約ルート。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Cargo {
    booking_id: BookingId,
    shipper_id: ShipperId,
    route_specification: RouteSpecification,
    consignee: Consignee,
    cargo_type: CargoType,
    weight: Weight,
    status: BookingStatus,
    dimensions: Option<Dimensions>,
    quantity: Option<Quantity>,
    description: Option<Description>,
    hazardous_declaration: Option<HazardousDeclaration>,
    temperature_requirement: Option<TemperatureRequirement>,
}

impl Cargo {
    /// 貨物予約を新規登録する。`BookingId` を採番し、状態は `Preliminary` になる。
    ///
    /// # Errors
    ///
    /// - 危険物なのに危険物申告が欠落 → `BookingError::MissingHazardousDeclaration`
    /// - 冷凍・冷蔵なのに温度管理条件が欠落 → `BookingError::MissingTemperatureRequirement`
    pub fn book(command: BookCargoCommand) -> Result<Self, BookingError> {
        match command.cargo_type {
            CargoType::Hazardous if command.hazardous_declaration.is_none() => {
                return Err(BookingError::MissingHazardousDeclaration);
            }
            CargoType::Refrigerated if command.temperature_requirement.is_none() => {
                return Err(BookingError::MissingTemperatureRequirement);
            }
            _ => {}
        }

        Ok(Self {
            booking_id: BookingId::generate(),
            shipper_id: command.shipper_id,
            route_specification: command.route_specification,
            consignee: command.consignee,
            cargo_type: command.cargo_type,
            weight: command.weight,
            status: BookingStatus::Preliminary,
            dimensions: command.dimensions,
            quantity: command.quantity,
            description: command.description,
            hazardous_declaration: command.hazardous_declaration,
            temperature_requirement: command.temperature_requirement,
        })
    }

    /// 永続化レコードから貨物集約を復元する（不変条件検証は行わない）。
    #[must_use]
    #[allow(clippy::too_many_arguments)]
    pub fn reconstitute(
        booking_id: BookingId,
        shipper_id: ShipperId,
        route_specification: RouteSpecification,
        consignee: Consignee,
        cargo_type: CargoType,
        weight: Weight,
        status: BookingStatus,
        dimensions: Option<Dimensions>,
        quantity: Option<Quantity>,
        description: Option<Description>,
        hazardous_declaration: Option<HazardousDeclaration>,
        temperature_requirement: Option<TemperatureRequirement>,
    ) -> Self {
        Self {
            booking_id,
            shipper_id,
            route_specification,
            consignee,
            cargo_type,
            weight,
            status,
            dimensions,
            quantity,
            description,
            hazardous_declaration,
            temperature_requirement,
        }
    }

    /// 予約 ID を返す。
    #[must_use]
    pub fn booking_id(&self) -> &BookingId {
        &self.booking_id
    }

    /// 荷主 ID を返す。
    #[must_use]
    pub fn shipper_id(&self) -> ShipperId {
        self.shipper_id
    }

    /// ルート仕様を返す。
    #[must_use]
    pub fn route_specification(&self) -> &RouteSpecification {
        &self.route_specification
    }

    /// 荷受人情報を返す。
    #[must_use]
    pub fn consignee(&self) -> &Consignee {
        &self.consignee
    }

    /// 貨物種別を返す。
    #[must_use]
    pub fn cargo_type(&self) -> CargoType {
        self.cargo_type
    }

    /// 重量を返す。
    #[must_use]
    pub fn weight(&self) -> Weight {
        self.weight
    }

    /// 予約状態を返す。
    #[must_use]
    pub fn status(&self) -> BookingStatus {
        self.status
    }

    /// 危険物申告を返す。
    #[must_use]
    pub fn hazardous_declaration(&self) -> Option<&HazardousDeclaration> {
        self.hazardous_declaration.as_ref()
    }

    /// 温度管理条件を返す。
    #[must_use]
    pub fn temperature_requirement(&self) -> Option<TemperatureRequirement> {
        self.temperature_requirement
    }

    /// 寸法を返す。
    #[must_use]
    pub fn dimensions(&self) -> Option<Dimensions> {
        self.dimensions
    }

    /// 個数を返す。
    #[must_use]
    pub fn quantity(&self) -> Option<Quantity> {
        self.quantity
    }

    /// 品名を返す。
    #[must_use]
    pub fn description(&self) -> Option<&Description> {
        self.description.as_ref()
    }

    // --- 状態遷移（予約ライフサイクル） ---------------------------------------

    /// 経路設計を依頼する（`Preliminary → RouteDesigning`・US06）。
    ///
    /// # Errors
    ///
    /// 仮受付以外の状態からは依頼できない → `InvalidStatusTransition`。
    pub fn request_route_design(&mut self) -> Result<(), BookingError> {
        match self.status {
            BookingStatus::Preliminary => {
                self.status = BookingStatus::RouteDesigning;
                Ok(())
            }
            other => Err(BookingError::InvalidStatusTransition {
                from: other.as_str(),
                action: "request_route_design",
            }),
        }
    }

    /// 確定経路を予約に紐付ける（`RouteDesigning → RouteProposed`・US11）。
    ///
    /// # Errors
    ///
    /// 経路設計中以外の状態からは紐付けできない → `InvalidStatusTransition`。
    pub fn propose_route(&mut self) -> Result<(), BookingError> {
        match self.status {
            BookingStatus::RouteDesigning => {
                self.status = BookingStatus::RouteProposed;
                Ok(())
            }
            other => Err(BookingError::InvalidStatusTransition {
                from: other.as_str(),
                action: "propose_route",
            }),
        }
    }

    /// 予約を確定する（`RouteProposed → Confirmed`・US13）。
    ///
    /// # Errors
    ///
    /// 経路提案中以外の状態からは確定できない → `InvalidStatusTransition`。
    pub fn confirm(&mut self) -> Result<(), BookingError> {
        match self.status {
            BookingStatus::RouteProposed => {
                self.status = BookingStatus::Confirmed;
                Ok(())
            }
            other => Err(BookingError::InvalidStatusTransition {
                from: other.as_str(),
                action: "confirm",
            }),
        }
    }

    /// ルート変更のため経路設計中へ差し戻す（`RouteProposed → RouteDesigning`・US13）。
    ///
    /// # Errors
    ///
    /// 経路提案中以外の状態からは差し戻せない → `InvalidStatusTransition`。
    pub fn revert_to_route_designing(&mut self) -> Result<(), BookingError> {
        match self.status {
            BookingStatus::RouteProposed => {
                self.status = BookingStatus::RouteDesigning;
                Ok(())
            }
            other => Err(BookingError::InvalidStatusTransition {
                from: other.as_str(),
                action: "revert_to_route_designing",
            }),
        }
    }

    /// 追跡番号を発行する（`Confirmed → TrackingIssued`・US14）。
    ///
    /// # Errors
    ///
    /// 予約確定以外の状態からは発行できない → `InvalidStatusTransition`。
    pub fn issue_tracking(&mut self) -> Result<(), BookingError> {
        match self.status {
            BookingStatus::Confirmed => {
                self.status = BookingStatus::TrackingIssued;
                Ok(())
            }
            other => Err(BookingError::InvalidStatusTransition {
                from: other.as_str(),
                action: "issue_tracking",
            }),
        }
    }

    /// 予約をキャンセルする（`→ Cancelled`・US13）。
    ///
    /// 確定前（`Preliminary`/`RouteDesigning`/`RouteProposed`）からのみキャンセル可能。
    /// 確定済み以降は輸送手配が進むため本 IT ではキャンセル不可とする。
    ///
    /// # Errors
    ///
    /// 確定済み以降・キャンセル済みからはキャンセルできない → `InvalidStatusTransition`。
    pub fn cancel(&mut self) -> Result<(), BookingError> {
        match self.status {
            BookingStatus::Preliminary
            | BookingStatus::RouteDesigning
            | BookingStatus::RouteProposed => {
                self.status = BookingStatus::Cancelled;
                Ok(())
            }
            other => Err(BookingError::InvalidStatusTransition {
                from: other.as_str(),
                action: "cancel",
            }),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::value_objects::TemperatureUnit;
    use chrono::NaiveDate;
    use rust_decimal::Decimal;
    use shared_kernel::Location;

    fn route() -> RouteSpecification {
        RouteSpecification::new(
            Location::new("JPOSA").unwrap(),
            Location::new("USLAX").unwrap(),
            NaiveDate::from_ymd_opt(2026, 4, 15).unwrap(),
        )
        .unwrap()
    }

    fn consignee() -> Consignee {
        Consignee::new("LA Trading Inc.", "contact@la-trading.example").unwrap()
    }

    fn base_command(cargo_type: CargoType) -> BookCargoCommand {
        BookCargoCommand {
            shipper_id: ShipperId::generate(),
            route_specification: route(),
            consignee: consignee(),
            cargo_type,
            weight: Weight::new(Decimal::new(1200, 0)).unwrap(),
            dimensions: None,
            quantity: None,
            description: None,
            hazardous_declaration: None,
            temperature_requirement: None,
        }
    }

    #[test]
    fn 一般貨物の予約は仮受付状態で登録される() {
        let cargo = Cargo::book(base_command(CargoType::General)).expect("登録成功");
        assert_eq!(cargo.status(), BookingStatus::Preliminary);
        assert!(cargo.booking_id().as_str().starts_with("BKG-"));
    }

    #[test]
    fn 危険物は危険物申告が無いとエラー() {
        let result = Cargo::book(base_command(CargoType::Hazardous));
        assert_eq!(result, Err(BookingError::MissingHazardousDeclaration));
    }

    #[test]
    fn 危険物は申告があれば登録できる() {
        let mut command = base_command(CargoType::Hazardous);
        command.hazardous_declaration =
            Some(HazardousDeclaration::new("3", "UN1203", "Gasoline").unwrap());
        assert!(Cargo::book(command).is_ok());
    }

    #[test]
    fn 冷凍貨物は温度条件が無いとエラー() {
        let result = Cargo::book(base_command(CargoType::Refrigerated));
        assert_eq!(result, Err(BookingError::MissingTemperatureRequirement));
    }

    #[test]
    fn 冷凍貨物は温度条件があれば登録できる() {
        let mut command = base_command(CargoType::Refrigerated);
        command.temperature_requirement = Some(
            TemperatureRequirement::new(
                Decimal::new(-20, 0),
                Decimal::new(-5, 0),
                TemperatureUnit::Celsius,
            )
            .unwrap(),
        );
        assert!(Cargo::book(command).is_ok());
    }

    fn preliminary_cargo() -> Cargo {
        Cargo::book(base_command(CargoType::General)).expect("登録成功")
    }

    #[test]
    fn 仮受付から経路設計中へ遷移できる() {
        let mut cargo = preliminary_cargo();
        assert!(cargo.request_route_design().is_ok());
        assert_eq!(cargo.status(), BookingStatus::RouteDesigning);
    }

    #[test]
    fn 仮受付以外からの経路設計依頼は拒否される() {
        let mut cargo = preliminary_cargo();
        cargo.request_route_design().unwrap();
        // 既に経路設計中なので再依頼は不可。
        assert_eq!(
            cargo.request_route_design(),
            Err(BookingError::InvalidStatusTransition {
                from: "ROUTE_DESIGNING",
                action: "request_route_design",
            })
        );
    }

    #[test]
    fn 経路設計中から経路提案中へ遷移できる() {
        let mut cargo = preliminary_cargo();
        cargo.request_route_design().unwrap();
        assert!(cargo.propose_route().is_ok());
        assert_eq!(cargo.status(), BookingStatus::RouteProposed);
    }

    #[test]
    fn 経路設計中以外からの経路紐付けは拒否される() {
        let mut cargo = preliminary_cargo();
        // 仮受付から直接の紐付けは不可。
        assert_eq!(
            cargo.propose_route(),
            Err(BookingError::InvalidStatusTransition {
                from: "PRELIMINARY",
                action: "propose_route",
            })
        );
    }

    #[test]
    fn 経路提案中から予約確定へ遷移できる() {
        let mut cargo = preliminary_cargo();
        cargo.request_route_design().unwrap();
        cargo.propose_route().unwrap();
        assert!(cargo.confirm().is_ok());
        assert_eq!(cargo.status(), BookingStatus::Confirmed);
    }

    #[test]
    fn 経路提案中以外からの予約確定は拒否される() {
        let mut cargo = preliminary_cargo();
        assert_eq!(
            cargo.confirm(),
            Err(BookingError::InvalidStatusTransition {
                from: "PRELIMINARY",
                action: "confirm",
            })
        );
    }

    #[test]
    fn 経路提案中から経路設計中へ差し戻せる() {
        let mut cargo = preliminary_cargo();
        cargo.request_route_design().unwrap();
        cargo.propose_route().unwrap();
        assert!(cargo.revert_to_route_designing().is_ok());
        assert_eq!(cargo.status(), BookingStatus::RouteDesigning);
    }

    #[test]
    fn 経路提案中以外からの差し戻しは拒否される() {
        let mut cargo = preliminary_cargo();
        cargo.request_route_design().unwrap();
        assert_eq!(
            cargo.revert_to_route_designing(),
            Err(BookingError::InvalidStatusTransition {
                from: "ROUTE_DESIGNING",
                action: "revert_to_route_designing",
            })
        );
    }

    #[test]
    fn 確定前の各状態からキャンセルできる() {
        // 仮受付から。
        let mut c1 = preliminary_cargo();
        assert!(c1.cancel().is_ok());
        assert_eq!(c1.status(), BookingStatus::Cancelled);
        // 経路設計中から。
        let mut c2 = preliminary_cargo();
        c2.request_route_design().unwrap();
        assert!(c2.cancel().is_ok());
        assert_eq!(c2.status(), BookingStatus::Cancelled);
        // 経路提案中から。
        let mut c3 = preliminary_cargo();
        c3.request_route_design().unwrap();
        c3.propose_route().unwrap();
        assert!(c3.cancel().is_ok());
        assert_eq!(c3.status(), BookingStatus::Cancelled);
    }

    #[test]
    fn 確定済みはキャンセルできない() {
        let mut cargo = preliminary_cargo();
        cargo.request_route_design().unwrap();
        cargo.propose_route().unwrap();
        cargo.confirm().unwrap();
        assert_eq!(
            cargo.cancel(),
            Err(BookingError::InvalidStatusTransition {
                from: "CONFIRMED",
                action: "cancel",
            })
        );
    }

    #[test]
    fn 確定済みから追跡番号発行済へ遷移できる() {
        let mut cargo = preliminary_cargo();
        cargo.request_route_design().unwrap();
        cargo.propose_route().unwrap();
        cargo.confirm().unwrap();
        assert!(cargo.issue_tracking().is_ok());
        assert_eq!(cargo.status(), BookingStatus::TrackingIssued);
    }

    #[test]
    fn 確定済み以外からの追跡番号発行は拒否される() {
        let mut cargo = preliminary_cargo();
        assert_eq!(
            cargo.issue_tracking(),
            Err(BookingError::InvalidStatusTransition {
                from: "PRELIMINARY",
                action: "issue_tracking",
            })
        );
    }

    #[test]
    fn 追跡番号発行済からの再発行は拒否される() {
        let mut cargo = preliminary_cargo();
        cargo.request_route_design().unwrap();
        cargo.propose_route().unwrap();
        cargo.confirm().unwrap();
        cargo.issue_tracking().unwrap();
        assert_eq!(
            cargo.issue_tracking(),
            Err(BookingError::InvalidStatusTransition {
                from: "TRACKING_ISSUED",
                action: "issue_tracking",
            })
        );
    }
}
