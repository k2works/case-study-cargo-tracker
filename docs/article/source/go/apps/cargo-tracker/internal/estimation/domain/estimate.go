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
	ErrEmptyArrivalDeadline   = errors.New("arrival deadline must not be empty")
	ErrPastArrivalDeadline    = errors.New("arrival deadline must be in the future")
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

// RouteCandidate はルート候補（航海番号・所要日数・概算コスト・経由港）を表す値オブジェクト。
type RouteCandidate struct {
	voyageNumber  string
	transitDays   int
	estimatedCost int64
	waypoints     []string
}

// NewRouteCandidate はバリデーション付きでルート候補を生成する。
// waypoints は経由港（無ければ空）。voyageNumber は複数区間の場合 "+" 連結表記。
func NewRouteCandidate(voyageNumber string, transitDays int, estimatedCost int64, waypoints []string) (RouteCandidate, error) {
	if strings.TrimSpace(voyageNumber) == "" {
		return RouteCandidate{}, ErrEmptyVoyageNumber
	}
	if transitDays <= 0 {
		return RouteCandidate{}, ErrNonPositiveTransitDays
	}
	if estimatedCost <= 0 {
		return RouteCandidate{}, ErrNonPositiveCost
	}
	cp := make([]string, len(waypoints))
	copy(cp, waypoints)
	return RouteCandidate{voyageNumber: voyageNumber, transitDays: transitDays, estimatedCost: estimatedCost, waypoints: cp}, nil
}

// VoyageNumber は航海番号を返す。
func (r RouteCandidate) VoyageNumber() string { return r.voyageNumber }

// TransitDays は所要日数を返す。
func (r RouteCandidate) TransitDays() int { return r.transitDays }

// EstimatedCost は概算コストを返す。
func (r RouteCandidate) EstimatedCost() int64 { return r.estimatedCost }

// Waypoints は経由港を返す（防御的コピー）。
func (r RouteCandidate) Waypoints() []string {
	cp := make([]string, len(r.waypoints))
	copy(cp, r.waypoints)
	return cp
}

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

// NewEstimateParams は見積新規作成のパラメータ集合（引数過多を避ける）。
type NewEstimateParams struct {
	Id              EstimateId
	Origin          shared.Location
	Destination     shared.Location
	ArrivalDeadline time.Time
	CargoType       shared.CargoType
	WeightKg        float64
	Candidates      []RouteCandidate
	Now             time.Time
}

// CreateEstimate は見積を新規作成する（US01）。
// ArrivalDeadline は非ゼロかつ Now より未来でなければならない（T6: 集約に不変条件を寄せる）。
func CreateEstimate(p NewEstimateParams) (*Estimate, error) {
	if p.Origin.SameAs(p.Destination) {
		return nil, ErrSameOriginDestination
	}
	if p.WeightKg <= 0 {
		return nil, ErrNonPositiveWeight
	}
	if p.ArrivalDeadline.IsZero() {
		return nil, ErrEmptyArrivalDeadline
	}
	if !p.ArrivalDeadline.After(p.Now) {
		return nil, ErrPastArrivalDeadline
	}
	cp := make([]RouteCandidate, len(p.Candidates))
	copy(cp, p.Candidates)
	return &Estimate{
		estimateId:      p.Id,
		origin:          p.Origin,
		destination:     p.Destination,
		arrivalDeadline: p.ArrivalDeadline,
		cargoType:       p.CargoType,
		weightKg:        p.WeightKg,
		candidates:      cp,
		status:          EstimateStatusCreated,
	}, nil
}

// EstimateSnapshot は永続化層からの再構築に用いるパラメータ集合（引数過多を避ける）。
type EstimateSnapshot struct {
	Id              EstimateId
	Origin          shared.Location
	Destination     shared.Location
	ArrivalDeadline time.Time
	CargoType       shared.CargoType
	WeightKg        float64
	Candidates      []RouteCandidate
	Status          EstimateStatus
}

// Restore は永続化層から Estimate を再構築する（Repository 専用）。
func Restore(s EstimateSnapshot) *Estimate {
	cp := make([]RouteCandidate, len(s.Candidates))
	copy(cp, s.Candidates)
	return &Estimate{estimateId: s.Id, origin: s.Origin, destination: s.Destination, arrivalDeadline: s.ArrivalDeadline, cargoType: s.CargoType, weightKg: s.WeightKg, candidates: cp, status: s.Status}
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
