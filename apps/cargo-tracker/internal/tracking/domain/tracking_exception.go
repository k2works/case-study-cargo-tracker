package domain

import (
	"errors"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// 例外ドメインのエラー。
var (
	ErrUnknownExceptionType = errors.New("unknown exception type")
	ErrExceptionNotFound    = errors.New("tracking exception not found")
)

// escalationThreshold は遅延（DELAY）のエスカレーション閾値（超過でエスカレーション）。
const escalationThreshold = 48 * time.Hour

// ExceptionType は追跡例外の種別を表す値オブジェクト。
type ExceptionType string

const (
	// ExceptionTypeDelay は遅延。
	ExceptionTypeDelay ExceptionType = "DELAY"
	// ExceptionTypeDamage は破損。
	ExceptionTypeDamage ExceptionType = "DAMAGE"
	// ExceptionTypeLost は紛失。
	ExceptionTypeLost ExceptionType = "LOST"
	// ExceptionTypeCustomsHold は税関保留。
	ExceptionTypeCustomsHold ExceptionType = "CUSTOMS_HOLD"
)

// ParseExceptionType は文字列から ExceptionType を解釈する。
func ParseExceptionType(v string) (ExceptionType, error) {
	switch ExceptionType(v) {
	case ExceptionTypeDelay, ExceptionTypeDamage, ExceptionTypeLost, ExceptionTypeCustomsHold:
		return ExceptionType(v), nil
	default:
		return "", ErrUnknownExceptionType
	}
}

// String は例外種別のコード表現を返す。
func (t ExceptionType) String() string { return string(t) }

// IsLost は紛失かを返す。
func (t ExceptionType) IsLost() bool { return t == ExceptionTypeLost }

// Ja は例外種別の日本語表示を返す。
func (t ExceptionType) Ja() string {
	switch t {
	case ExceptionTypeDelay:
		return "遅延"
	case ExceptionTypeDamage:
		return "破損"
	case ExceptionTypeLost:
		return "紛失"
	case ExceptionTypeCustomsHold:
		return "税関保留"
	default:
		return string(t)
	}
}

// EscalationPolicy は例外のエスカレーション要否を判定するドメインサービス。
//
//	LOST  : 即時エスカレーション（経過時間に依らず true）
//	DELAY : occurredAt から 48 時間を超過でエスカレーション（`>` 判定）
//	その他: エスカレーション不要
type EscalationPolicy struct{}

// RequiresEscalation はエスカレーション要否を判定する。
func (EscalationPolicy) RequiresEscalation(t ExceptionType, occurredAt, now time.Time) bool {
	switch t {
	case ExceptionTypeLost:
		return true
	case ExceptionTypeDelay:
		return now.Sub(occurredAt) > escalationThreshold
	default:
		return false
	}
}

// TrackingExceptionEvent は追跡例外イベント（集約内エンティティ）。
type TrackingExceptionEvent struct {
	exceptionType   ExceptionType
	location        shared.Location
	occurredAt      time.Time
	description     string
	escalationFlag  bool
	resolvedAt      time.Time
	resolutionNotes string
}

// NewTrackingExceptionEvent は未解決の追跡例外イベントを生成する。
func NewTrackingExceptionEvent(exceptionType ExceptionType, location shared.Location, occurredAt time.Time, description string, escalationFlag bool) TrackingExceptionEvent {
	return TrackingExceptionEvent{
		exceptionType:  exceptionType,
		location:       location,
		occurredAt:     occurredAt,
		description:    description,
		escalationFlag: escalationFlag,
	}
}

// ReconstructTrackingExceptionEvent は永続化データから例外イベントを再構築する。
func ReconstructTrackingExceptionEvent(exceptionType ExceptionType, location shared.Location, occurredAt time.Time, description string, escalationFlag bool, resolvedAt time.Time, resolutionNotes string) TrackingExceptionEvent {
	return TrackingExceptionEvent{
		exceptionType:   exceptionType,
		location:        location,
		occurredAt:      occurredAt,
		description:     description,
		escalationFlag:  escalationFlag,
		resolvedAt:      resolvedAt,
		resolutionNotes: resolutionNotes,
	}
}

// ExceptionType は例外種別を返す。
func (e TrackingExceptionEvent) ExceptionType() ExceptionType { return e.exceptionType }

// Location は発生場所を返す。
func (e TrackingExceptionEvent) Location() shared.Location { return e.location }

// OccurredAt は発生日時を返す。
func (e TrackingExceptionEvent) OccurredAt() time.Time { return e.occurredAt }

// Description は発生状況の説明を返す。
func (e TrackingExceptionEvent) Description() string { return e.description }

// EscalationFlag はエスカレーションフラグを返す。
func (e TrackingExceptionEvent) EscalationFlag() bool { return e.escalationFlag }

// ResolvedAt は解決日時を返す（ゼロ値=未解決）。
func (e TrackingExceptionEvent) ResolvedAt() time.Time { return e.resolvedAt }

// ResolutionNotes は対応内容メモを返す。
func (e TrackingExceptionEvent) ResolutionNotes() string { return e.resolutionNotes }

// IsResolved は解決済みかを返す。
func (e TrackingExceptionEvent) IsResolved() bool { return !e.resolvedAt.IsZero() }
