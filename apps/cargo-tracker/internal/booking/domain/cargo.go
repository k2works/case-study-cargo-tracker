package domain

import (
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// Cargo は予約コンテキストの集約ルート。貨物予約の状態・旅程・配送状況を統括する。
// IT1 スコープでは登録（PRELIMINARY）までを扱う。
type Cargo struct {
	bookingID     BookingId
	shipperID     shared.ShipperId
	routeSpec     RouteSpecification
	cargoType     CargoType
	weight        Weight
	bookingAmount Money
	status        BookingStatus
}

// RegisterCargo は貨物予約を新規登録する（PRELIMINARY 状態で作成）。
func RegisterCargo(
	bookingID BookingId,
	shipperID shared.ShipperId,
	routeSpec RouteSpecification,
	cargoType CargoType,
	weight Weight,
	bookingAmount Money,
) (*Cargo, error) {
	return &Cargo{
		bookingID:     bookingID,
		shipperID:     shipperID,
		routeSpec:     routeSpec,
		cargoType:     cargoType,
		weight:        weight,
		bookingAmount: bookingAmount,
		status:        BookingStatusPreliminary,
	}, nil
}

// BookingID は予約 ID を返す。
func (c *Cargo) BookingID() BookingId { return c.bookingID }

// ShipperID は荷主識別子を返す。
func (c *Cargo) ShipperID() shared.ShipperId { return c.shipperID }

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
