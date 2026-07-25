package domain

import (
	"errors"
	"strings"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// 確定経路（CargoItinerary/Leg）のドメインエラー（US09）。
var (
	ErrEmptyLegVoyage        = errors.New("leg voyage number must not be empty")
	ErrSameLegLoadUnload     = errors.New("leg load and unload locations must differ")
	ErrInvalidLegTimes       = errors.New("leg load time must be before unload time")
	ErrEmptyItinerary        = errors.New("itinerary must have at least one leg")
	ErrDisconnectedItinerary = errors.New("itinerary legs must be connected in space")
	ErrOutOfOrderItinerary   = errors.New("itinerary legs must be in chronological order")
)

// Leg は確定経路を構成する 1 区間を表す値オブジェクト。
// 航海参照は BC 独立性のため業務識別子 voyageNumber（文字列）で保持する（ADR-0005）。
type Leg struct {
	voyageNumber   string
	loadLocation   shared.Location
	unloadLocation shared.Location
	loadTime       time.Time
	unloadTime     time.Time
}

// NewLeg はバリデーション付きで Leg を生成する。
func NewLeg(voyageNumber string, load, unload shared.Location, loadTime, unloadTime time.Time) (Leg, error) {
	if strings.TrimSpace(voyageNumber) == "" {
		return Leg{}, ErrEmptyLegVoyage
	}
	if load.SameAs(unload) {
		return Leg{}, ErrSameLegLoadUnload
	}
	if !loadTime.Before(unloadTime) {
		return Leg{}, ErrInvalidLegTimes
	}
	return Leg{voyageNumber: voyageNumber, loadLocation: load, unloadLocation: unload, loadTime: loadTime, unloadTime: unloadTime}, nil
}

// VoyageNumber は航海番号を返す。
func (l Leg) VoyageNumber() string { return l.voyageNumber }

// LoadLocation は積込地を返す。
func (l Leg) LoadLocation() shared.Location { return l.loadLocation }

// UnloadLocation は荷降地を返す。
func (l Leg) UnloadLocation() shared.Location { return l.unloadLocation }

// LoadTime は積込時刻を返す。
func (l Leg) LoadTime() time.Time { return l.loadTime }

// UnloadTime は荷降時刻を返す。
func (l Leg) UnloadTime() time.Time { return l.unloadTime }

// CargoItinerary は確定経路（Leg 列）を表す値オブジェクト。
// 空間連結（前区間の荷降地＝次区間の積込地）・時刻連続（次の積込は前の荷降以降）を不変条件とする。
type CargoItinerary struct {
	legs []Leg
}

// NewCargoItinerary は連結・時刻制約を検証して CargoItinerary を生成する（US09）。
func NewCargoItinerary(legs []Leg) (CargoItinerary, error) {
	if len(legs) == 0 {
		return CargoItinerary{}, ErrEmptyItinerary
	}
	for i := 1; i < len(legs); i++ {
		if !legs[i-1].unloadLocation.SameAs(legs[i].loadLocation) {
			return CargoItinerary{}, ErrDisconnectedItinerary
		}
		if legs[i].loadTime.Before(legs[i-1].unloadTime) {
			return CargoItinerary{}, ErrOutOfOrderItinerary
		}
	}
	cp := make([]Leg, len(legs))
	copy(cp, legs)
	return CargoItinerary{legs: cp}, nil
}

// Legs は区間の並びを返す（防御的コピー）。
func (i CargoItinerary) Legs() []Leg {
	cp := make([]Leg, len(i.legs))
	copy(cp, i.legs)
	return cp
}

// Origin は経路の起点を返す。
func (i CargoItinerary) Origin() shared.Location { return i.legs[0].loadLocation }

// Destination は経路の終点を返す。
func (i CargoItinerary) Destination() shared.Location { return i.legs[len(i.legs)-1].unloadLocation }

// ExpectedArrivalTime は終点への到着予定時刻（最終区間の荷降時刻）を返す。
func (i CargoItinerary) ExpectedArrivalTime() time.Time { return i.legs[len(i.legs)-1].unloadTime }

// IsEmpty は経路が未設定かを返す。
func (i CargoItinerary) IsEmpty() bool { return len(i.legs) == 0 }

// Delivery は配送状況を表す値オブジェクト。IT4 では経路状態（RoutingStatus）のみを保持する。
// transportStatus・最終荷役は IT6 で追加予定。
type Delivery struct {
	routingStatus shared.RoutingStatus
}

// NewDelivery は指定した経路状態で Delivery を生成する。
func NewDelivery(routingStatus shared.RoutingStatus) Delivery {
	return Delivery{routingStatus: routingStatus}
}

// RoutingStatus は経路状態を返す。
func (d Delivery) RoutingStatus() shared.RoutingStatus { return d.routingStatus }
