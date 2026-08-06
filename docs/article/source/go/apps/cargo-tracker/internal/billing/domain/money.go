// Package domain は Billing Context のドメインモデル（Invoice 集約・値オブジェクト）を提供する。
package domain

import (
	"errors"
	"math"
)

// ErrCurrencyMismatch は通貨が一致しない金額演算で返される。
var ErrCurrencyMismatch = errors.New("currency mismatch")

// Money は金額を表す値オブジェクト。amount は最小通貨単位（円は 1 円単位）の整数で保持する。
// 参考実装は BigDecimal だが、本プロジェクトは通貨最小単位の int64 で丸め誤差を排除する
// （data-model の金額 INTEGER・既存 booking.Money と一貫・IT8 注1）。
type Money struct {
	amount   int64
	currency string
}

// NewMoney は金額を生成する。
func NewMoney(amount int64, currency string) Money {
	return Money{amount: amount, currency: currency}
}

// Amount は最小通貨単位の金額を返す。
func (m Money) Amount() int64 { return m.amount }

// Currency は通貨コードを返す。
func (m Money) Currency() string { return m.currency }

// IsZero は金額がゼロかを返す。
func (m Money) IsZero() bool { return m.amount == 0 }

// Add は同一通貨の金額を加算する。
func (m Money) Add(other Money) (Money, error) {
	if m.currency != other.currency {
		return Money{}, ErrCurrencyMismatch
	}
	return Money{amount: m.amount + other.amount, currency: m.currency}, nil
}

// Subtract は同一通貨の金額を減算する。
func (m Money) Subtract(other Money) (Money, error) {
	if m.currency != other.currency {
		return Money{}, ErrCurrencyMismatch
	}
	return Money{amount: m.amount - other.amount, currency: m.currency}, nil
}

// MultiplyRate は金額に率（0.0〜）を乗じ、最小通貨単位で四捨五入した金額を返す。
// 割引額（× 割引率）・消費税額（× 税率）の算出に用いる。
func (m Money) MultiplyRate(rate float64) Money {
	return Money{amount: int64(math.Round(float64(m.amount) * rate)), currency: m.currency}
}
