// Package domain は全コンテキストで共有する共有カーネル（Shared Kernel）を提供する。
package domain

import "errors"

// ErrEmptyShipperId は ShipperId が空の場合に返される。
var ErrEmptyShipperId = errors.New("shipper id must not be empty")

// ShipperId は荷主を一意に識別する共有カーネルの値オブジェクト。
// Booking Context と Shipper Context で共有する。
type ShipperId struct {
	value string
}

// NewShipperId はバリデーション付きで ShipperId を生成する。
func NewShipperId(value string) (ShipperId, error) {
	if value == "" {
		return ShipperId{}, ErrEmptyShipperId
	}
	return ShipperId{value: value}, nil
}

// Value は ShipperId の文字列表現を返す。
func (s ShipperId) Value() string { return s.value }
