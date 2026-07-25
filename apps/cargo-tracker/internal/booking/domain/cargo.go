package domain

import (
	"errors"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// US13: 状態遷移のエラー。
var (
	// ErrInvalidStatusTransition は許容されない状態遷移を試みた場合に返される。
	ErrInvalidStatusTransition = errors.New("invalid booking status transition")
	// ErrCargoNotFound は指定予約が存在しない場合に返される。
	ErrCargoNotFound = errors.New("cargo not found")
)

// Cargo は予約コンテキストの集約ルート。貨物予約の状態・旅程・配送状況を統括する。
// 荷主参照は BC 独立性のため業務識別子 ShipperCode で保持する（ADR-0005）。
type Cargo struct {
	bookingID     BookingId
	shipperCode   shared.ShipperCode
	routeSpec     RouteSpecification
	cargoType     CargoType
	weight        Weight
	bookingAmount Money
	status        BookingStatus
	hazardous     *HazardousDeclaration
	temperature   *TemperatureRequirement
}

// CargoParams は Cargo の生成・復元に用いるパラメータ集合。
// 引数過多を避け、呼び出し側の可読性を高める。
type CargoParams struct {
	BookingID     BookingId
	ShipperCode   shared.ShipperCode
	RouteSpec     RouteSpecification
	CargoType     CargoType
	Weight        Weight
	BookingAmount Money
	Hazardous     *HazardousDeclaration
	Temperature   *TemperatureRequirement
}

// RegisterCargo は貨物予約を新規登録する（PRELIMINARY 状態で作成）。
// 貨物種別に応じて特殊貨物情報の整合を検証する（US05）:
//   - HAZARDOUS は危険物申告が必須（温度条件は不可）
//   - REFRIGERATED は温度管理条件が必須（危険物申告は不可）
//   - GENERAL はいずれも不可
func RegisterCargo(p CargoParams) (*Cargo, error) {
	if err := validateSpecialCargo(p.CargoType, p.Hazardous, p.Temperature); err != nil {
		return nil, err
	}
	return &Cargo{
		bookingID:     p.BookingID,
		shipperCode:   p.ShipperCode,
		routeSpec:     p.RouteSpec,
		cargoType:     p.CargoType,
		weight:        p.Weight,
		bookingAmount: p.BookingAmount,
		status:        BookingStatusPreliminary,
		hazardous:     p.Hazardous,
		temperature:   p.Temperature,
	}, nil
}

// validateSpecialCargo は貨物種別と特殊貨物情報の整合を検証する。
func validateSpecialCargo(cargoType CargoType, hazardous *HazardousDeclaration, temperature *TemperatureRequirement) error {
	switch cargoType {
	case CargoTypeHazardous:
		if hazardous == nil {
			return ErrHazardousDeclRequired
		}
		if temperature != nil {
			return ErrSpecialInfoNotAllowed
		}
	case CargoTypeRefrigerated:
		if temperature == nil {
			return ErrTemperatureReqRequired
		}
		if hazardous != nil {
			return ErrSpecialInfoNotAllowed
		}
	case CargoTypeGeneral:
		if hazardous != nil || temperature != nil {
			return ErrSpecialInfoNotAllowed
		}
	}
	return nil
}

// Confirm は予約を確定する（PRELIMINARY / ROUTE_PROPOSED → CONFIRMED）。
func (c *Cargo) Confirm() error {
	if !c.CanConfirm() {
		return ErrInvalidStatusTransition
	}
	c.status = BookingStatusConfirmed
	return nil
}

// Cancel は予約をキャンセルする（PRELIMINARY / ROUTE_PROPOSED / CONFIRMED → CANCELLED）。
func (c *Cargo) Cancel() error {
	if !c.CanCancel() {
		return ErrInvalidStatusTransition
	}
	c.status = BookingStatusCancelled
	return nil
}

// CanConfirm は確定操作が許容される状態かを返す（UI 制御とドメイン規則の単一情報源）。
func (c *Cargo) CanConfirm() bool {
	return c.status == BookingStatusPreliminary || c.status == BookingStatusRouteProposed
}

// CanCancel はキャンセル操作が許容される状態かを返す。
func (c *Cargo) CanCancel() bool {
	return c.status == BookingStatusPreliminary ||
		c.status == BookingStatusRouteProposed ||
		c.status == BookingStatusConfirmed
}

// CanSendBackToRouting は経路再設計への差し戻しが許容される状態かを返す。
func (c *Cargo) CanSendBackToRouting() bool {
	return c.status == BookingStatusRouteProposed || c.status == BookingStatusConfirmed
}

// CanAssignToRouting は経路設計者への引き渡しが許容される状態かを返す（US06）。
func (c *Cargo) CanAssignToRouting() bool {
	return c.status == BookingStatusPreliminary
}

// AssignToRouting は予約を経路設計者に引き渡す（PRELIMINARY → ROUTE_PROPOSED）。
// domain-model.md:447・ui_design.md:579 の正典に準拠し新状態は追加しない（US06）。
func (c *Cargo) AssignToRouting() error {
	if !c.CanAssignToRouting() {
		return ErrInvalidStatusTransition
	}
	c.status = BookingStatusRouteProposed
	return nil
}

// SendBackToRouting は予約を経路再設計へ差し戻す（ROUTE_PROPOSED / CONFIRMED → PRELIMINARY）。
func (c *Cargo) SendBackToRouting() error {
	if !c.CanSendBackToRouting() {
		return ErrInvalidStatusTransition
	}
	c.status = BookingStatusPreliminary
	return nil
}

// BookingID は予約 ID を返す。
func (c *Cargo) BookingID() BookingId { return c.bookingID }

// ShipperCode は荷主参照コードを返す。
func (c *Cargo) ShipperCode() shared.ShipperCode { return c.shipperCode }

// RouteSpec はルート仕様を返す。
func (c *Cargo) RouteSpec() RouteSpecification { return c.routeSpec }

// CargoType は貨物種別を返す。
func (c *Cargo) CargoType() CargoType { return c.cargoType }

// Weight は貨物重量を返す。
func (c *Cargo) Weight() Weight { return c.weight }

// BookingAmount は予約金額を返す。
func (c *Cargo) BookingAmount() Money { return c.bookingAmount }

// Status は予約状態を返す。
func (c *Cargo) Status() BookingStatus { return c.status }

// HazardousDeclaration は危険物申告を返す（危険物でなければ nil）。
func (c *Cargo) HazardousDeclaration() *HazardousDeclaration { return c.hazardous }

// TemperatureRequirement は温度管理条件を返す（冷凍貨物でなければ nil）。
func (c *Cargo) TemperatureRequirement() *TemperatureRequirement { return c.temperature }

// Restore は永続化層から集約を再構築する（Repository 専用）。
// 状態・特殊貨物情報を含めて任意の状態の Cargo を復元する。
func Restore(p CargoParams, status BookingStatus) *Cargo {
	return &Cargo{
		bookingID:     p.BookingID,
		shipperCode:   p.ShipperCode,
		routeSpec:     p.RouteSpec,
		cargoType:     p.CargoType,
		weight:        p.Weight,
		bookingAmount: p.BookingAmount,
		status:        status,
		hazardous:     p.Hazardous,
		temperature:   p.Temperature,
	}
}
