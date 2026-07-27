// Package domain は Handling Context のドメインモデル（HandlingActivity 集約・値オブジェクト）を提供する。
package domain

import (
	"errors"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ErrUnknownHandlingType は未知の荷役種別を解釈しようとした場合に返される。
var ErrUnknownHandlingType = errors.New("unknown handling type")

// HandlingType は荷役種別を表す値オブジェクト。
type HandlingType string

const (
	// HandlingTypeReceive は受領。
	HandlingTypeReceive HandlingType = "RECEIVE"
	// HandlingTypeLoad は積込。
	HandlingTypeLoad HandlingType = "LOAD"
	// HandlingTypeUnload は荷降し。
	HandlingTypeUnload HandlingType = "UNLOAD"
	// HandlingTypeCustoms は通関。
	HandlingTypeCustoms HandlingType = "CUSTOMS"
	// HandlingTypeClaim は引取。
	HandlingTypeClaim HandlingType = "CLAIM"
)

// ParseHandlingType は文字列から HandlingType を解釈する。
func ParseHandlingType(v string) (HandlingType, error) {
	switch HandlingType(v) {
	case HandlingTypeReceive, HandlingTypeLoad, HandlingTypeUnload, HandlingTypeCustoms, HandlingTypeClaim:
		return HandlingType(v), nil
	default:
		return "", ErrUnknownHandlingType
	}
}

// String は荷役種別のコード表現を返す。
func (t HandlingType) String() string { return string(t) }

// RequiresVoyageNumber は VoyageNumber が必須かを返す（LOAD / UNLOAD が必須）。
func (t HandlingType) RequiresVoyageNumber() bool {
	return t == HandlingTypeLoad || t == HandlingTypeUnload
}

// IsClaimType は引取作業かを返す。
func (t HandlingType) IsClaimType() bool { return t == HandlingTypeClaim }

// ResultingTransportStatus は荷役完了後に遷移する輸送状態を返す。
// CUSTOMS は輸送フェーズを進めないため UNKNOWN（現状態維持）を返す。
func (t HandlingType) ResultingTransportStatus() shared.TransportStatus {
	switch t {
	case HandlingTypeReceive:
		return shared.TransportStatusReceived
	case HandlingTypeLoad:
		return shared.TransportStatusLoaded
	case HandlingTypeUnload:
		return shared.TransportStatusUnloaded
	case HandlingTypeClaim:
		return shared.TransportStatusClaimed
	default:
		return shared.TransportStatusUnknown
	}
}

// Ja は荷役種別の日本語表示を返す。
func (t HandlingType) Ja() string {
	switch t {
	case HandlingTypeReceive:
		return "受領"
	case HandlingTypeLoad:
		return "積込"
	case HandlingTypeUnload:
		return "荷降し"
	case HandlingTypeCustoms:
		return "通関"
	case HandlingTypeClaim:
		return "引取"
	default:
		return string(t)
	}
}
