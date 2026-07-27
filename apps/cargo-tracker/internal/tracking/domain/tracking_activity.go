// Package domain は Tracking Context のドメインモデル（TrackingActivity 集約・値オブジェクト）を提供する。
package domain

import (
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ドメインエラー。
var (
	ErrInvalidTrackingNumber  = errors.New("tracking number must match TRK-YYYYMMDD-NNNN")
	ErrEmptyTrackingBookingId = errors.New("tracking booking id must not be empty")
)

var trackingNumberPattern = regexp.MustCompile(`^TRK-\d{8}-\d{4}$`)

// TrackingNumber は追跡番号を表す値オブジェクト（形式 TRK-YYYYMMDD-NNNN）。
type TrackingNumber struct {
	value string
}

// NewTrackingNumber はバリデーション付きで追跡番号を生成する。
func NewTrackingNumber(v string) (TrackingNumber, error) {
	if !trackingNumberPattern.MatchString(v) {
		return TrackingNumber{}, ErrInvalidTrackingNumber
	}
	return TrackingNumber{value: v}, nil
}

// FormatTrackingNumber は発行日と連番から追跡番号文字列を組み立てる。
func FormatTrackingNumber(day time.Time, seq int) string {
	return fmt.Sprintf("TRK-%s-%04d", day.Format("20060102"), seq)
}

// Value は追跡番号の文字列表現を返す。
func (n TrackingNumber) Value() string { return n.value }

// TrackingActivityEvent は時系列で記録される追跡イベント。
type TrackingActivityEvent struct {
	eventType       string
	location        shared.Location
	completionTime  time.Time
	voyageNumber    string
	transportStatus shared.TransportStatus
}

// NewTrackingActivityEvent は追跡イベントを生成する。
func NewTrackingActivityEvent(eventType string, location shared.Location, completionTime time.Time, voyageNumber string, transportStatus shared.TransportStatus) TrackingActivityEvent {
	return TrackingActivityEvent{
		eventType:       eventType,
		location:        location,
		completionTime:  completionTime,
		voyageNumber:    voyageNumber,
		transportStatus: transportStatus,
	}
}

// EventType は荷役種別コードを返す。
func (e TrackingActivityEvent) EventType() string { return e.eventType }

// Location は発生場所を返す。
func (e TrackingActivityEvent) Location() shared.Location { return e.location }

// CompletionTime は発生時刻を返す。
func (e TrackingActivityEvent) CompletionTime() time.Time { return e.completionTime }

// VoyageNumber は航海番号を返す。
func (e TrackingActivityEvent) VoyageNumber() string { return e.voyageNumber }

// TransportStatus はこのイベントで遷移した輸送状態を返す。
func (e TrackingActivityEvent) TransportStatus() shared.TransportStatus { return e.transportStatus }

// TrackingActivity は貨物の追跡情報全体を管理する集約ルート。
type TrackingActivity struct {
	trackingNumber TrackingNumber
	bookingId      string
	events         []TrackingActivityEvent
	exceptions     []TrackingExceptionEvent
}

// NewTrackingActivity は追跡レコードを新規作成する（初期状態は受領待ち）。
func NewTrackingActivity(trackingNumber TrackingNumber, bookingId string) (TrackingActivity, error) {
	if strings.TrimSpace(bookingId) == "" {
		return TrackingActivity{}, ErrEmptyTrackingBookingId
	}
	return TrackingActivity{trackingNumber: trackingNumber, bookingId: bookingId}, nil
}

// ReconstructTrackingActivity は永続化データから追跡レコードを再構築する。
func ReconstructTrackingActivity(trackingNumber TrackingNumber, bookingId string, events []TrackingActivityEvent, exceptions []TrackingExceptionEvent) TrackingActivity {
	ce := make([]TrackingActivityEvent, len(events))
	copy(ce, events)
	cx := make([]TrackingExceptionEvent, len(exceptions))
	copy(cx, exceptions)
	return TrackingActivity{trackingNumber: trackingNumber, bookingId: bookingId, events: ce, exceptions: cx}
}

// AddEvent は追跡イベントを時系列末尾に追加する。
func (a *TrackingActivity) AddEvent(event TrackingActivityEvent) {
	a.events = append(a.events, event)
}

// AddException は追跡例外イベントを追加する（US19/US20）。
func (a *TrackingActivity) AddException(ex TrackingExceptionEvent) {
	a.exceptions = append(a.exceptions, ex)
}

// ResolveException は index 番目の例外を解決し、対応内容と解決日時を記録する。
// 既に解決済みの例外は二重解決を防ぐためエラーを返す。
func (a *TrackingActivity) ResolveException(index int, resolutionNotes string, resolvedAt time.Time) error {
	if index < 0 || index >= len(a.exceptions) {
		return ErrExceptionNotFound
	}
	if a.exceptions[index].IsResolved() {
		return ErrExceptionAlreadyResolved
	}
	a.exceptions[index].resolvedAt = resolvedAt
	a.exceptions[index].resolutionNotes = resolutionNotes
	return nil
}

// HasActiveException は未解決の例外を持つかを返す。
func (a TrackingActivity) HasActiveException() bool {
	for _, ex := range a.exceptions {
		if !ex.IsResolved() {
			return true
		}
	}
	return false
}

// Exceptions は例外イベント一覧（防御的コピー）を返す。
func (a TrackingActivity) Exceptions() []TrackingExceptionEvent {
	cp := make([]TrackingExceptionEvent, len(a.exceptions))
	copy(cp, a.exceptions)
	return cp
}

// TrackingNumber は追跡番号を返す。
func (a TrackingActivity) TrackingNumber() TrackingNumber { return a.trackingNumber }

// BookingId は予約 ID を返す。
func (a TrackingActivity) BookingId() string { return a.bookingId }

// Events は追跡イベント一覧（防御的コピー）を返す。
func (a TrackingActivity) Events() []TrackingActivityEvent {
	cp := make([]TrackingActivityEvent, len(a.events))
	copy(cp, a.events)
	return cp
}

// CurrentStatus は最新の有効な輸送状態を返す。イベントが無ければ受領待ち。
// 未解決の例外があれば EXCEPTION（解決すると発生前状態に復帰する・US19/US20）。
// UNKNOWN（通関 CUSTOMS 等・輸送フェーズを進めないイベント）は現状態を退行させないため読み飛ばす。
func (a TrackingActivity) CurrentStatus() shared.TransportStatus {
	if a.HasActiveException() {
		return shared.TransportStatusException
	}
	for i := len(a.events) - 1; i >= 0; i-- {
		if s := a.events[i].transportStatus; s != shared.TransportStatusUnknown {
			return s
		}
	}
	return shared.TransportStatusNotReceived
}

// CurrentLocation は最新イベントの発生場所を返す。イベントが無ければゼロ値。
func (a TrackingActivity) CurrentLocation() shared.Location {
	if len(a.events) == 0 {
		return shared.Location{}
	}
	return a.events[len(a.events)-1].location
}
