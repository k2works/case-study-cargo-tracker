package domain

// TransportStatus は貨物の輸送フェーズを表す共有カーネルの列挙型。
// Booking / Tracking / Handling コンテキストで共有する（9 段階）。
type TransportStatus string

const (
	// TransportStatusNotReceived は受領待ち（追跡番号発行直後）。
	TransportStatusNotReceived TransportStatus = "NOT_RECEIVED"
	// TransportStatusReceived は出発港で受領済み。
	TransportStatusReceived TransportStatus = "RECEIVED"
	// TransportStatusLoaded は船舶へ積込済み。
	TransportStatusLoaded TransportStatus = "LOADED"
	// TransportStatusOnboardCarrier は輸送中（出港済み）。
	TransportStatusOnboardCarrier TransportStatus = "ONBOARD_CARRIER"
	// TransportStatusUnloaded は荷降し済み。
	TransportStatusUnloaded TransportStatus = "UNLOADED"
	// TransportStatusAwaitingClaim は引取待ち（目的港到着）。
	TransportStatusAwaitingClaim TransportStatus = "AWAITING_CLAIM"
	// TransportStatusClaimed は引取済み（配送完了）。
	TransportStatusClaimed TransportStatus = "CLAIMED"
	// TransportStatusException は例外発生中。
	TransportStatusException TransportStatus = "EXCEPTION"
	// TransportStatusUnknown は状態不明。
	TransportStatusUnknown TransportStatus = "UNKNOWN"
)

// Ja は輸送状態の日本語表示を返す。
func (s TransportStatus) Ja() string {
	switch s {
	case TransportStatusNotReceived:
		return "受領待ち"
	case TransportStatusReceived:
		return "受領済"
	case TransportStatusLoaded:
		return "積込済"
	case TransportStatusOnboardCarrier:
		return "輸送中"
	case TransportStatusUnloaded:
		return "荷降し済"
	case TransportStatusAwaitingClaim:
		return "引取待ち"
	case TransportStatusClaimed:
		return "引取済"
	case TransportStatusException:
		return "例外"
	case TransportStatusUnknown:
		return "不明"
	default:
		return string(s)
	}
}

// IsValid は既知の輸送状態かどうかを返す。
func (s TransportStatus) IsValid() bool {
	switch s {
	case TransportStatusNotReceived, TransportStatusReceived, TransportStatusLoaded,
		TransportStatusOnboardCarrier, TransportStatusUnloaded, TransportStatusAwaitingClaim,
		TransportStatusClaimed, TransportStatusException, TransportStatusUnknown:
		return true
	default:
		return false
	}
}
