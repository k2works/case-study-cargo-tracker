// Package domain は Routing Context のドメインモデル（Voyage 集約・値オブジェクト）を提供する。
package domain

import (
	"errors"
	"strings"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ドメインエラー。
var (
	ErrEmptyVoyageNumber    = errors.New("voyage number must not be empty")
	ErrSameDepartureArrival = errors.New("departure and arrival locations must differ")
	ErrInvalidMovementDates = errors.New("departure time must be before arrival time")
	ErrEmptySchedule        = errors.New("schedule must have at least one movement")
	ErrDisconnectedSchedule = errors.New("schedule movements must be connected in sequence")
	ErrEmptyVesselName      = errors.New("vessel name must not be empty")
	ErrEmptyCarrier         = errors.New("carrier must not be empty")
	ErrEmptyCargoTypes      = errors.New("supported cargo types must not be empty")
)

// VoyageNumber は航海を一意に識別する Routing Context 固有の値オブジェクト。
type VoyageNumber struct {
	value string
}

// NewVoyageNumber はバリデーション付きで VoyageNumber を生成する。
func NewVoyageNumber(v string) (VoyageNumber, error) {
	if strings.TrimSpace(v) == "" {
		return VoyageNumber{}, ErrEmptyVoyageNumber
	}
	return VoyageNumber{value: v}, nil
}

// Value は航海番号の文字列表現を返す。
func (v VoyageNumber) Value() string { return v.value }

// CarrierMovement は運送区間（出発地・到着地・出発/到着時刻・順序）を表すエンティティ。
type CarrierMovement struct {
	departure     shared.Location
	arrival       shared.Location
	departureTime time.Time
	arrivalTime   time.Time
	seq           int
}

// NewCarrierMovement はバリデーション付きで運送区間を生成する。
func NewCarrierMovement(departure, arrival shared.Location, departureTime, arrivalTime time.Time, seq int) (CarrierMovement, error) {
	if departure.SameAs(arrival) {
		return CarrierMovement{}, ErrSameDepartureArrival
	}
	if !departureTime.Before(arrivalTime) {
		return CarrierMovement{}, ErrInvalidMovementDates
	}
	return CarrierMovement{departure: departure, arrival: arrival, departureTime: departureTime, arrivalTime: arrivalTime, seq: seq}, nil
}

// Departure は出発地を返す。
func (c CarrierMovement) Departure() shared.Location { return c.departure }

// Arrival は到着地を返す。
func (c CarrierMovement) Arrival() shared.Location { return c.arrival }

// DepartureTime は出発時刻を返す。
func (c CarrierMovement) DepartureTime() time.Time { return c.departureTime }

// ArrivalTime は到着時刻を返す。
func (c CarrierMovement) ArrivalTime() time.Time { return c.arrivalTime }

// Seq は区間の順序を返す。
func (c CarrierMovement) Seq() int { return c.seq }

// Schedule は時系列順の運送区間の並びを表す値オブジェクト。
type Schedule struct {
	movements []CarrierMovement
}

// NewSchedule は連結制約（前区間の到着地 == 次区間の出発地）を検証して Schedule を生成する。
func NewSchedule(movements []CarrierMovement) (Schedule, error) {
	if len(movements) == 0 {
		return Schedule{}, ErrEmptySchedule
	}
	for i := 1; i < len(movements); i++ {
		if !movements[i-1].Arrival().SameAs(movements[i].Departure()) {
			return Schedule{}, ErrDisconnectedSchedule
		}
	}
	cp := make([]CarrierMovement, len(movements))
	copy(cp, movements)
	return Schedule{movements: cp}, nil
}

// Movements は運送区間の並びを返す（防御的コピー）。
func (s Schedule) Movements() []CarrierMovement {
	cp := make([]CarrierMovement, len(s.movements))
	copy(cp, s.movements)
	return cp
}

// Origin はスケジュールの起点（最初の区間の出発地）を返す。
func (s Schedule) Origin() shared.Location { return s.movements[0].Departure() }

// Destination はスケジュールの終点（最後の区間の到着地）を返す。
func (s Schedule) Destination() shared.Location { return s.movements[len(s.movements)-1].Arrival() }

// Voyage は Routing Context の集約ルート。航海スケジュールを管理する。
type Voyage struct {
	voyageNumber        VoyageNumber
	vesselName          string
	carrier             string
	schedule            Schedule
	supportedCargoTypes []shared.CargoType
}

// RegisterVoyage は航海スケジュールを新規登録する（US24）。
func RegisterVoyage(voyageNumber VoyageNumber, vesselName, carrier string, schedule Schedule, supportedCargoTypes []shared.CargoType) (*Voyage, error) {
	if strings.TrimSpace(vesselName) == "" {
		return nil, ErrEmptyVesselName
	}
	if strings.TrimSpace(carrier) == "" {
		return nil, ErrEmptyCarrier
	}
	if len(supportedCargoTypes) == 0 {
		return nil, ErrEmptyCargoTypes
	}
	cts := make([]shared.CargoType, len(supportedCargoTypes))
	copy(cts, supportedCargoTypes)
	return &Voyage{
		voyageNumber:        voyageNumber,
		vesselName:          vesselName,
		carrier:             carrier,
		schedule:            schedule,
		supportedCargoTypes: cts,
	}, nil
}

// Restore は永続化層から Voyage を再構築する（Repository 専用）。
func Restore(voyageNumber VoyageNumber, vesselName, carrier string, schedule Schedule, supportedCargoTypes []shared.CargoType) *Voyage {
	cts := make([]shared.CargoType, len(supportedCargoTypes))
	copy(cts, supportedCargoTypes)
	return &Voyage{voyageNumber: voyageNumber, vesselName: vesselName, carrier: carrier, schedule: schedule, supportedCargoTypes: cts}
}

// VoyageNumber は航海番号を返す。
func (v *Voyage) VoyageNumber() VoyageNumber { return v.voyageNumber }

// VesselName は船名を返す。
func (v *Voyage) VesselName() string { return v.vesselName }

// Carrier は運送会社を返す。
func (v *Voyage) Carrier() string { return v.carrier }

// Schedule は航海スケジュールを返す。
func (v *Voyage) Schedule() Schedule { return v.schedule }

// SupportedCargoTypes は対応貨物種別を返す（防御的コピー）。
func (v *Voyage) SupportedCargoTypes() []shared.CargoType {
	cts := make([]shared.CargoType, len(v.supportedCargoTypes))
	copy(cts, v.supportedCargoTypes)
	return cts
}

// Supports は指定貨物種別に対応するかを返す。
func (v *Voyage) Supports(ct shared.CargoType) bool {
	for _, c := range v.supportedCargoTypes {
		if c == ct {
			return true
		}
	}
	return false
}

// Origin はスケジュールの起点を返す。
func (v *Voyage) Origin() shared.Location { return v.schedule.Origin() }

// Destination はスケジュールの終点を返す。
func (v *Voyage) Destination() shared.Location { return v.schedule.Destination() }

// UpdateSchedule はスケジュールと属性を更新する（US25）。
func (v *Voyage) UpdateSchedule(vesselName, carrier string, schedule Schedule, supportedCargoTypes []shared.CargoType) error {
	if strings.TrimSpace(vesselName) == "" {
		return ErrEmptyVesselName
	}
	if strings.TrimSpace(carrier) == "" {
		return ErrEmptyCarrier
	}
	if len(supportedCargoTypes) == 0 {
		return ErrEmptyCargoTypes
	}
	cts := make([]shared.CargoType, len(supportedCargoTypes))
	copy(cts, supportedCargoTypes)
	v.vesselName = vesselName
	v.carrier = carrier
	v.schedule = schedule
	v.supportedCargoTypes = cts
	return nil
}
