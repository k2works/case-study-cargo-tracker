package domain

import "errors"

// ErrInvalidLocation は UN/LOCODE が不正な場合に返される。
var ErrInvalidLocation = errors.New("location must be a 5-character UN/LOCODE")

// Location は UN/LOCODE で識別される港湾・地点を表す共有カーネルの値オブジェクト。
type Location struct {
	unLocode string
}

// NewLocation はバリデーション付きで Location を生成する（UN/LOCODE は 5 文字）。
func NewLocation(unLocode string) (Location, error) {
	if len(unLocode) != 5 {
		return Location{}, ErrInvalidLocation
	}
	return Location{unLocode: unLocode}, nil
}

// UnLocode は UN/LOCODE 文字列を返す。
func (l Location) UnLocode() string { return l.unLocode }

// SameAs は同一地点かどうかを返す。
func (l Location) SameAs(other Location) bool { return l.unLocode == other.unLocode }

// IsZero は未設定かどうかを返す。
func (l Location) IsZero() bool { return l.unLocode == "" }
