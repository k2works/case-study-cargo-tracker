package domain

import (
	"errors"
	"strings"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ドメインエラー。
var (
	ErrEmptyCargoBookingId           = errors.New("cargo booking id must not be empty")
	ErrZeroLocation                  = errors.New("handling location must not be empty")
	ErrVoyageNumberRequired          = errors.New("voyage number is required for load/unload")
	ErrConsigneeConfirmationRequired = errors.New("consignee confirmation is required for claim")
)

// LegSnapshot は CargoSnapshot 内の旅程区間（積込港・荷降港・航海番号）を表す値オブジェクト。
type LegSnapshot struct {
	loadLocation   shared.Location
	unloadLocation shared.Location
	voyageNumber   string
}

// NewLegSnapshot は旅程区間スナップショットを生成する。
func NewLegSnapshot(load, unload shared.Location, voyageNumber string) LegSnapshot {
	return LegSnapshot{loadLocation: load, unloadLocation: unload, voyageNumber: voyageNumber}
}

// LoadLocation は積込港を返す。
func (l LegSnapshot) LoadLocation() shared.Location { return l.loadLocation }

// UnloadLocation は荷降港を返す。
func (l LegSnapshot) UnloadLocation() shared.Location { return l.unloadLocation }

// VoyageNumber は航海番号を返す。
func (l LegSnapshot) VoyageNumber() string { return l.voyageNumber }

// CargoSnapshot は ACL 経由で取得した貨物情報の不変スナップショット。荷役妥当性検証に用いる。
type CargoSnapshot struct {
	bookingId     string
	origin        shared.Location
	destination   shared.Location
	legs          []LegSnapshot
	routingStatus shared.RoutingStatus
}

// NewCargoSnapshot は貨物スナップショットを生成する。
func NewCargoSnapshot(bookingId string, origin, destination shared.Location, legs []LegSnapshot, routingStatus shared.RoutingStatus) CargoSnapshot {
	cp := make([]LegSnapshot, len(legs))
	copy(cp, legs)
	return CargoSnapshot{bookingId: bookingId, origin: origin, destination: destination, legs: cp, routingStatus: routingStatus}
}

// BookingId は予約 ID を返す。
func (s CargoSnapshot) BookingId() string { return s.bookingId }

// Origin は出発港を返す。
func (s CargoSnapshot) Origin() shared.Location { return s.origin }

// Destination は目的港を返す。
func (s CargoSnapshot) Destination() shared.Location { return s.destination }

// Legs は旅程区間を返す。
func (s CargoSnapshot) Legs() []LegSnapshot {
	cp := make([]LegSnapshot, len(s.legs))
	copy(cp, s.legs)
	return cp
}

// ValidationResult は荷役妥当性検証の結果を表す。
type ValidationResult struct {
	matched   bool
	misrouted bool
	warning   bool
}

// Matched は荷役場所が旅程・仕様と一致したかを返す。
func (r ValidationResult) Matched() bool { return r.matched }

// Misrouted は経路不整合（MISROUTED 確定）かを返す。
func (r ValidationResult) Misrouted() bool { return r.misrouted }

// Warning は警告（不一致だが MISROUTED ではない）かを返す。
func (r ValidationResult) Warning() bool { return r.warning }

// HandlingActivity は荷役作業を表す集約ルート。
type HandlingActivity struct {
	cargoBookingId        string
	handlingType          HandlingType
	location              shared.Location
	completionTime        time.Time
	voyageNumber          string
	consigneeConfirmation string
	operatorName          string
}

// NewHandlingActivity はバリデーション付きで荷役作業を生成する。
// LOAD / UNLOAD は voyageNumber 必須、CLAIM は consigneeConfirmation（署名/確認コード）必須。
func NewHandlingActivity(
	cargoBookingId string,
	handlingType HandlingType,
	location shared.Location,
	completionTime time.Time,
	voyageNumber string,
	consigneeConfirmation string,
	operatorName string,
) (HandlingActivity, error) {
	if strings.TrimSpace(cargoBookingId) == "" {
		return HandlingActivity{}, ErrEmptyCargoBookingId
	}
	if location.IsZero() {
		return HandlingActivity{}, ErrZeroLocation
	}
	if handlingType.RequiresVoyageNumber() && strings.TrimSpace(voyageNumber) == "" {
		return HandlingActivity{}, ErrVoyageNumberRequired
	}
	if handlingType.IsClaimType() && strings.TrimSpace(consigneeConfirmation) == "" {
		return HandlingActivity{}, ErrConsigneeConfirmationRequired
	}
	return HandlingActivity{
		cargoBookingId:        cargoBookingId,
		handlingType:          handlingType,
		location:              location,
		completionTime:        completionTime,
		voyageNumber:          voyageNumber,
		consigneeConfirmation: consigneeConfirmation,
		operatorName:          operatorName,
	}, nil
}

// IsValidFor は貨物スナップショットに対する荷役妥当性検証（デシジョンテーブル）を行う。
//
//	RECEIVE: 出発港一致。不一致で警告
//	LOAD:    Leg.loadLocation 一致。不一致で MISROUTED
//	UNLOAD:  Leg.unloadLocation 一致。不一致で MISROUTED
//	CLAIM:   目的港一致。不一致で警告
//	CUSTOMS: 場所チェックなし（常に妥当）
func (a HandlingActivity) IsValidFor(snapshot CargoSnapshot) ValidationResult {
	switch a.handlingType {
	case HandlingTypeReceive:
		return warnResult(a.location.SameAs(snapshot.origin))
	case HandlingTypeClaim:
		return warnResult(a.location.SameAs(snapshot.destination))
	case HandlingTypeLoad:
		return misrouteResult(a.matchesAnyLeg(snapshot, func(l LegSnapshot) shared.Location { return l.loadLocation }))
	case HandlingTypeUnload:
		return misrouteResult(a.matchesAnyLeg(snapshot, func(l LegSnapshot) shared.Location { return l.unloadLocation }))
	default: // CUSTOMS
		return ValidationResult{matched: true}
	}
}

func (a HandlingActivity) matchesAnyLeg(snapshot CargoSnapshot, pick func(LegSnapshot) shared.Location) bool {
	for _, leg := range snapshot.legs {
		if a.location.SameAs(pick(leg)) {
			return true
		}
	}
	return false
}

func warnResult(matched bool) ValidationResult {
	return ValidationResult{matched: matched, warning: !matched}
}

func misrouteResult(matched bool) ValidationResult {
	return ValidationResult{matched: matched, misrouted: !matched}
}

// CargoBookingId は貨物予約 ID を返す。
func (a HandlingActivity) CargoBookingId() string { return a.cargoBookingId }

// Type は荷役種別を返す。
func (a HandlingActivity) Type() HandlingType { return a.handlingType }

// Location は荷役場所を返す。
func (a HandlingActivity) Location() shared.Location { return a.location }

// CompletionTime は作業完了時刻を返す。
func (a HandlingActivity) CompletionTime() time.Time { return a.completionTime }

// VoyageNumber は航海番号を返す。
func (a HandlingActivity) VoyageNumber() string { return a.voyageNumber }

// ConsigneeConfirmation は荷受人確認（署名/確認コード）を返す。
func (a HandlingActivity) ConsigneeConfirmation() string { return a.consigneeConfirmation }

// OperatorName は作業員名を返す。
func (a HandlingActivity) OperatorName() string { return a.operatorName }

// ResultingTransportStatus は荷役完了後に遷移する輸送状態を返す。
func (a HandlingActivity) ResultingTransportStatus() shared.TransportStatus {
	return a.handlingType.ResultingTransportStatus()
}
