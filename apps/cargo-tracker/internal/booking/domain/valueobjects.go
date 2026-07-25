// Package domain は Booking Context のドメインモデル（Cargo 集約・値オブジェクト）を提供する。
package domain

import (
	"errors"
	"strings"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ドメインエラー。
var (
	ErrEmptyBookingId        = errors.New("booking id must not be empty")
	ErrUnknownCargoType      = errors.New("unknown cargo type")
	ErrSameOriginDestination = errors.New("origin and destination must differ")
	ErrNonPositiveWeight     = errors.New("weight must be greater than 0")

	// US05: 特殊貨物のエラー。
	ErrEmptyHazardClass         = errors.New("hazard class must not be empty")
	ErrEmptyUNNumber            = errors.New("UN number must not be empty")
	ErrEmptyProperShippingName  = errors.New("proper shipping name must not be empty")
	ErrUnknownTemperatureUnit   = errors.New("unknown temperature unit")
	ErrTemperatureRangeInverted = errors.New("min temperature must not exceed max temperature")
	ErrHazardousDeclRequired    = errors.New("hazardous declaration is required for HAZARDOUS cargo")
	ErrTemperatureReqRequired   = errors.New("temperature requirement is required for REFRIGERATED cargo")
	ErrSpecialInfoNotAllowed    = errors.New("special cargo info is only allowed for its matching cargo type")
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

// Ja は予約状態の日本語表示を返す。
func (s BookingStatus) Ja() string {
	switch s {
	case BookingStatusPreliminary:
		return "仮受付"
	case BookingStatusRouteProposed:
		return "経路提案済み"
	case BookingStatusConfirmed:
		return "予約確定"
	case BookingStatusTrackingIssued:
		return "追跡番号発行済み"
	case BookingStatusInTransit:
		return "輸送中"
	case BookingStatusDelivered:
		return "配達済み"
	case BookingStatusSettled:
		return "精算済み"
	case BookingStatusCancelled:
		return "キャンセル"
	default:
		return string(s)
	}
}

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

// HazardousDeclaration は危険物申告を表す値オブジェクト（US05）。
// 危険物クラス・UN 番号・正式輸送品名をすべて必須で保持する。
type HazardousDeclaration struct {
	hazardClass        string
	unNumber           string
	properShippingName string
}

// NewHazardousDeclaration はバリデーション付きで危険物申告を生成する。
func NewHazardousDeclaration(hazardClass, unNumber, properShippingName string) (HazardousDeclaration, error) {
	if strings.TrimSpace(hazardClass) == "" {
		return HazardousDeclaration{}, ErrEmptyHazardClass
	}
	if strings.TrimSpace(unNumber) == "" {
		return HazardousDeclaration{}, ErrEmptyUNNumber
	}
	if strings.TrimSpace(properShippingName) == "" {
		return HazardousDeclaration{}, ErrEmptyProperShippingName
	}
	return HazardousDeclaration{hazardClass: hazardClass, unNumber: unNumber, properShippingName: properShippingName}, nil
}

// HazardClass は危険物クラスを返す。
func (h HazardousDeclaration) HazardClass() string { return h.hazardClass }

// UNNumber は UN 番号を返す。
func (h HazardousDeclaration) UNNumber() string { return h.unNumber }

// ProperShippingName は正式輸送品名を返す。
func (h HazardousDeclaration) ProperShippingName() string { return h.properShippingName }

// TemperatureUnit は温度単位を表す列挙型。
type TemperatureUnit string

const (
	// TemperatureUnitCelsius は摂氏。
	TemperatureUnitCelsius TemperatureUnit = "CELSIUS"
	// TemperatureUnitFahrenheit は華氏。
	TemperatureUnitFahrenheit TemperatureUnit = "FAHRENHEIT"
)

// ParseTemperatureUnit は文字列から TemperatureUnit を解釈する。
func ParseTemperatureUnit(v string) (TemperatureUnit, error) {
	switch TemperatureUnit(v) {
	case TemperatureUnitCelsius, TemperatureUnitFahrenheit:
		return TemperatureUnit(v), nil
	default:
		return "", ErrUnknownTemperatureUnit
	}
}

// TemperatureRequirement は温度管理条件を表す値オブジェクト（US05）。
// 最低温度が最高温度を超えてはならない。
type TemperatureRequirement struct {
	minTemp float64
	maxTemp float64
	unit    TemperatureUnit
}

// NewTemperatureRequirement はバリデーション付きで温度管理条件を生成する。
func NewTemperatureRequirement(minTemp, maxTemp float64, unit TemperatureUnit) (TemperatureRequirement, error) {
	if _, err := ParseTemperatureUnit(string(unit)); err != nil {
		return TemperatureRequirement{}, err
	}
	if minTemp > maxTemp {
		return TemperatureRequirement{}, ErrTemperatureRangeInverted
	}
	return TemperatureRequirement{minTemp: minTemp, maxTemp: maxTemp, unit: unit}, nil
}

// MinTemperature は最低温度を返す。
func (t TemperatureRequirement) MinTemperature() float64 { return t.minTemp }

// MaxTemperature は最高温度を返す。
func (t TemperatureRequirement) MaxTemperature() float64 { return t.maxTemp }

// Unit は温度単位を返す。
func (t TemperatureRequirement) Unit() TemperatureUnit { return t.unit }
