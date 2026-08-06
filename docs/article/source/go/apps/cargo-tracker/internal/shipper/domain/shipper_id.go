package domain

import "errors"

// ErrEmptyShipperId は ShipperId が空の場合に返される。
var ErrEmptyShipperId = errors.New("shipper id must not be empty")

// ShipperId は荷主を一意に識別する内部識別子（UUID ベース）。
// BC 独立性のため Shipper Context 内部に閉じる。BC 間の荷主参照は
// 業務識別子 shared.ShipperCode を用いる（ADR-0005）。
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
