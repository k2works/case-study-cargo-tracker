package domain

import "errors"

// ErrEmptyShipperCode は ShipperCode が空の場合に返される。
var ErrEmptyShipperCode = errors.New("shipper code must not be empty")

// ShipperCode は境界付けられたコンテキスト間で荷主を参照する業務識別子（SHP-XXXXXX 形式）。
// Booking Context など Shipper 以外の BC は、UUID 内部 ID ではなくこのコードで荷主を参照する（ADR-0005）。
type ShipperCode struct {
	value string
}

// NewShipperCode はバリデーション付きで ShipperCode を生成する。
func NewShipperCode(value string) (ShipperCode, error) {
	if value == "" {
		return ShipperCode{}, ErrEmptyShipperCode
	}
	return ShipperCode{value: value}, nil
}

// Value は ShipperCode の文字列表現を返す。
func (c ShipperCode) Value() string { return c.value }
