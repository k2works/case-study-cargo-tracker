// Package domain は Booking Context のドメインモデル（Cargo 集約・値オブジェクト）を提供する。
package domain

import (
	"errors"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ドメインエラー。
var (
	ErrEmptyBookingId        = errors.New("booking id must not be empty")
	ErrUnknownCargoType      = errors.New("unknown cargo type")
	ErrSameOriginDestination = errors.New("origin and destination must differ")
	ErrNonPositiveWeight     = errors.New("weight must be greater than 0")
)

// BookingId は予約を一意に識別する値オブジェクト。
type BookingId struct {
	value string
}

// NewBookingId はバリデーション付きで BookingId を生成する。
func NewBookingId(v string) (BookingId, error) {
	if v == "" {
		return BookingId{}, ErrEmptyBookingId
	}
	return BookingId{value: v}, nil
}

// Value は予約 ID の文字列表現を返す。
func (b BookingId) Value() string { return b.value }

// CargoType は貨物種別を表す列挙型。
type CargoType string

const (
	// CargoTypeGeneral は一般貨物。
	CargoTypeGeneral CargoType = "GENERAL"
	// CargoTypeHazardous は危険物。
	CargoTypeHazardous CargoType = "HAZARDOUS"
	// CargoTypeRefrigerated は冷凍・冷蔵貨物。
	CargoTypeRefrigerated CargoType = "REFRIGERATED"
)

// ParseCargoType は文字列から CargoType を解釈する。
func ParseCargoType(v string) (CargoType, error) {
	switch CargoType(v) {
	case CargoTypeGeneral, CargoTypeHazardous, CargoTypeRefrigerated:
		return CargoType(v), nil
	default:
		return "", ErrUnknownCargoType
	}
}

// BookingStatus は予約ライフサイクルの状態を表す列挙型。
type BookingStatus string

const (
	// BookingStatusPreliminary は仮受付。
	BookingStatusPreliminary BookingStatus = "PRELIMINARY"
	// BookingStatusRouteProposed は経路提案済み。
	BookingStatusRouteProposed BookingStatus = "ROUTE_PROPOSED"
	// BookingStatusConfirmed は確定済み。
	BookingStatusConfirmed BookingStatus = "CONFIRMED"
	// BookingStatusTrackingIssued は追跡番号発行済み。
	BookingStatusTrackingIssued BookingStatus = "TRACKING_ISSUED"
	// BookingStatusInTransit は輸送中。
	BookingStatusInTransit BookingStatus = "IN_TRANSIT"
	// BookingStatusDelivered は配達済み。
	BookingStatusDelivered BookingStatus = "DELIVERED"
	// BookingStatusSettled は精算済み。
	BookingStatusSettled BookingStatus = "SETTLED"
	// BookingStatusCancelled はキャンセル済み。
	BookingStatusCancelled BookingStatus = "CANCELLED"
)

// RouteSpecification は出発地・目的地・到着期限の要件を表す値オブジェクト。
type RouteSpecification struct {
	origin          shared.Location
	destination     shared.Location
	arrivalDeadline time.Time
}

// NewRouteSpecification はバリデーション付きで RouteSpecification を生成する。
func NewRouteSpecification(origin, destination shared.Location, arrivalDeadline time.Time) (RouteSpecification, error) {
	if origin.SameAs(destination) {
		return RouteSpecification{}, ErrSameOriginDestination
	}
	return RouteSpecification{origin: origin, destination: destination, arrivalDeadline: arrivalDeadline}, nil
}

// Origin は出発地を返す。
func (r RouteSpecification) Origin() shared.Location { return r.origin }

// Destination は目的地を返す。
func (r RouteSpecification) Destination() shared.Location { return r.destination }

// ArrivalDeadline は到着期限を返す。
func (r RouteSpecification) ArrivalDeadline() time.Time { return r.arrivalDeadline }

// Weight は貨物重量（kg）を表す値オブジェクト。
type Weight struct {
	kg float64
}

// NewWeight は正の値でバリデーションして Weight を生成する。
func NewWeight(kg float64) (Weight, error) {
	if kg <= 0 {
		return Weight{}, ErrNonPositiveWeight
	}
	return Weight{kg: kg}, nil
}

// Kg は重量（kg）を返す。
func (w Weight) Kg() float64 { return w.kg }

// Money は金額と通貨コードのペアを表す値オブジェクト。
type Money struct {
	amount   int64
	currency string
}

// NewMoney は Money を生成する。
func NewMoney(amount int64, currency string) Money {
	return Money{amount: amount, currency: currency}
}

// Amount は金額を返す。
func (m Money) Amount() int64 { return m.amount }

// Currency は通貨コードを返す。
func (m Money) Currency() string { return m.currency }
