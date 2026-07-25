// Package domain は Estimation Context のドメインモデル（Estimate 集約・値オブジェクト）を提供する。
package domain

import (
	"errors"
	"strings"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ドメインエラー。
var (
	ErrEmptyEstimateId        = errors.New("estimate id must not be empty")
	ErrEmptyVoyageNumber      = errors.New("voyage number must not be empty")
	ErrNonPositiveTransitDays = errors.New("transit days must be greater than 0")
	ErrNonPositiveCost        = errors.New("estimated cost must be greater than 0")
	ErrSameOriginDestination  = errors.New("origin and destination must differ")
	ErrNonPositiveWeight      = errors.New("weight must be greater than 0")
)

// EstimateId は見積を一意に識別する値オブジェクト（UUID ベース）。
type EstimateId struct {
	value string
}

// NewEstimateId はバリデーション付きで EstimateId を生成する。
func NewEstimateId(v string) (EstimateId, error) {
	if strings.TrimSpace(v) == "" {
		return EstimateId{}, ErrEmptyEstimateId
	}
	return EstimateId{value: v}, nil
}

// Value は見積 ID の文字列表現を返す。
func (e EstimateId) Value() string { return e.value }

// EstimateStatus は見積状態を表す列挙型。
type EstimateStatus string

const (
	// EstimateStatusCreated は作成済み。
	EstimateStatusCreated EstimateStatus = "CREATED"
	// EstimateStatusExpired は期限切れ。
	EstimateStatusExpired EstimateStatus = "EXPIRED"
)

// Ja は見積状態の日本語表示を返す。
func (s EstimateStatus) Ja() string {
	switch s {
	case EstimateStatusCreated:
		return "作成済み"
	case EstimateStatusExpired:
		return "期限切れ"
	default:
		return string(s)
	}
}

// RouteCandidate はルート候補（航海番号・所要日数・概算コスト）を表す値オブジェクト。
type RouteCandidate struct {
	voyageNumber  string
	transitDays   int
	estimatedCost int64
}

// NewRouteCandidate はバリデーション付きでルート候補を生成する。
func NewRouteCandidate(voyageNumber string, transitDays int, estimatedCost int64) (RouteCandidate, error) {
	if strings.TrimSpace(voyageNumber) == "" {
		return RouteCandidate{}, ErrEmptyVoyageNumber
	}
	if transitDays <= 0 {
		return RouteCandidate{}, ErrNonPositiveTransitDays
	}
	if estimatedCost <= 0 {
		return RouteCandidate{}, ErrNonPositiveCost
	}
	return RouteCandidate{voyageNumber: voyageNumber, transitDays: transitDays, estimatedCost: estimatedCost}, nil
}

// VoyageNumber は航海番号を返す。
func (r RouteCandidate) VoyageNumber() string { return r.voyageNumber }

// TransitDays は所要日数を返す。
func (r RouteCandidate) TransitDays() int { return r.transitDays }

// EstimatedCost は概算コストを返す。
func (r RouteCandidate) EstimatedCost() int64 { return r.estimatedCost }

// Estimate は Estimation Context の集約ルート。輸送見積とルート候補を管理する。
type Estimate struct {
	estimateId      EstimateId
	origin          shared.Location
	destination     shared.Location
	arrivalDeadline time.Time
	cargoType       shared.CargoType
	weightKg        float64
	candidates      []RouteCandidate
	status          EstimateStatus
}

// CreateEstimate は見積を新規作成する（US01）。
func CreateEstimate(id EstimateId, origin, destination shared.Location, arrivalDeadline time.Time, cargoType shared.CargoType, weightKg float64, candidates []RouteCandidate) (*Estimate, error) {
	if origin.SameAs(destination) {
		return nil, ErrSameOriginDestination
	}
	if weightKg <= 0 {
		return nil, ErrNonPositiveWeight
	}
	cp := make([]RouteCandidate, len(candidates))
	copy(cp, candidates)
	return &Estimate{
		estimateId:      id,
		origin:          origin,
		destination:     destination,
		arrivalDeadline: arrivalDeadline,
		cargoType:       cargoType,
		weightKg:        weightKg,
		candidates:      cp,
		status:          EstimateStatusCreated,
	}, nil
}

// Restore は永続化層から Estimate を再構築する（Repository 専用）。
func Restore(id EstimateId, origin, destination shared.Location, arrivalDeadline time.Time, cargoType shared.CargoType, weightKg float64, candidates []RouteCandidate, status EstimateStatus) *Estimate {
	cp := make([]RouteCandidate, len(candidates))
	copy(cp, candidates)
	return &Estimate{estimateId: id, origin: origin, destination: destination, arrivalDeadline: arrivalDeadline, cargoType: cargoType, weightKg: weightKg, candidates: cp, status: status}
}

// EstimateId は見積 ID を返す。
func (e *Estimate) EstimateId() EstimateId { return e.estimateId }

// Origin は出発地を返す。
func (e *Estimate) Origin() shared.Location { return e.origin }

// Destination は仕向地を返す。
func (e *Estimate) Destination() shared.Location { return e.destination }

// ArrivalDeadline は希望到着期限を返す。
func (e *Estimate) ArrivalDeadline() time.Time { return e.arrivalDeadline }

// CargoType は貨物種別を返す。
func (e *Estimate) CargoType() shared.CargoType { return e.cargoType }

// WeightKg は重量を返す。
func (e *Estimate) WeightKg() float64 { return e.weightKg }

// Candidates はルート候補を返す（防御的コピー）。
func (e *Estimate) Candidates() []RouteCandidate {
	cp := make([]RouteCandidate, len(e.candidates))
	copy(cp, e.candidates)
	return cp
}

// Status は見積状態を返す。
func (e *Estimate) Status() EstimateStatus { return e.status }
