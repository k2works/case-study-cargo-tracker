package domain

import "time"

// Clock は現在時刻を返す横断ポート。time.Now への直接依存を排し、テストの決定性を確保する。
type Clock interface {
	Now() time.Time
}

// SystemClock は本番用の Clock 実装（time.Now を返す）。
type SystemClock struct{}

// Now は現在時刻を返す。
func (SystemClock) Now() time.Time { return time.Now() }

// FixedClock はテスト用の固定時刻 Clock。
type FixedClock struct {
	Fixed time.Time
}

// Now は固定時刻を返す。
func (c FixedClock) Now() time.Time { return c.Fixed }
