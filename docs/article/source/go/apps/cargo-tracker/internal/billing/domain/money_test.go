package domain_test

import (
	"testing"

	billing "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewMoney(t *testing.T) {
	m := billing.NewMoney(1000, "JPY")
	assert.Equal(t, int64(1000), m.Amount())
	assert.Equal(t, "JPY", m.Currency())
}

func TestMoney_Add(t *testing.T) {
	a := billing.NewMoney(1000, "JPY")
	b := billing.NewMoney(500, "JPY")
	sum, err := a.Add(b)
	require.NoError(t, err)
	assert.Equal(t, int64(1500), sum.Amount())
}

func TestMoney_Add_CurrencyMismatch(t *testing.T) {
	a := billing.NewMoney(1000, "JPY")
	b := billing.NewMoney(500, "USD")
	_, err := a.Add(b)
	assert.ErrorIs(t, err, billing.ErrCurrencyMismatch)
}

func TestMoney_Subtract(t *testing.T) {
	a := billing.NewMoney(1000, "JPY")
	b := billing.NewMoney(300, "JPY")
	diff, err := a.Subtract(b)
	require.NoError(t, err)
	assert.Equal(t, int64(700), diff.Amount())
}

func TestMoney_MultiplyRate(t *testing.T) {
	// 割引額 = 1000 × 0.10 = 100（四捨五入）
	assert.Equal(t, int64(100), billing.NewMoney(1000, "JPY").MultiplyRate(0.10).Amount())
	// 端数は四捨五入: 999 × 0.10 = 99.9 → 100
	assert.Equal(t, int64(100), billing.NewMoney(999, "JPY").MultiplyRate(0.10).Amount())
	// 消費税 10%: 90000 × 0.10 = 9000
	assert.Equal(t, int64(9000), billing.NewMoney(90000, "JPY").MultiplyRate(0.10).Amount())
}

func TestMoney_IsZero(t *testing.T) {
	assert.True(t, billing.NewMoney(0, "JPY").IsZero())
	assert.False(t, billing.NewMoney(1, "JPY").IsZero())
}
