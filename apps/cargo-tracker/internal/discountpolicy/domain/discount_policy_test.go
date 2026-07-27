package domain_test

import (
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func validParams() domain.NewDiscountPolicyParams {
	return domain.NewDiscountPolicyParams{
		ID:          "11111111-1111-1111-1111-111111111111",
		PolicyType:  domain.PolicyTypeCorporateStandard,
		Rate:        0.15,
		ValidFrom:   time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC),
		ValidUntil:  nil,
		Description: "法人標準割引",
	}
}

func TestNewDiscountPolicy_Valid(t *testing.T) {
	p, err := domain.NewDiscountPolicy(validParams())
	require.NoError(t, err)
	assert.Equal(t, domain.PolicyTypeCorporateStandard, p.PolicyType())
	assert.InDelta(t, 0.15, p.Rate(), 1e-9)
	assert.Equal(t, "法人標準割引", p.Description())
}

func TestNewDiscountPolicy_RejectsRateOutOfRange(t *testing.T) {
	params := validParams()
	params.Rate = 0.31
	_, err := domain.NewDiscountPolicy(params)
	assert.ErrorIs(t, err, domain.ErrInvalidRate)

	params.Rate = -0.01
	_, err = domain.NewDiscountPolicy(params)
	assert.ErrorIs(t, err, domain.ErrInvalidRate)
}

func TestNewDiscountPolicy_RejectsInvalidPolicyType(t *testing.T) {
	params := validParams()
	params.PolicyType = "UNKNOWN"
	_, err := domain.NewDiscountPolicy(params)
	assert.ErrorIs(t, err, domain.ErrInvalidPolicyType)
}

func TestNewDiscountPolicy_RejectsInvalidPeriod(t *testing.T) {
	params := validParams()
	until := time.Date(2025, 12, 31, 0, 0, 0, 0, time.UTC)
	params.ValidUntil = &until
	_, err := domain.NewDiscountPolicy(params)
	assert.ErrorIs(t, err, domain.ErrInvalidPeriod)
}

func TestDiscountPolicy_IsActiveOn(t *testing.T) {
	params := validParams()
	until := time.Date(2026, 12, 31, 0, 0, 0, 0, time.UTC)
	params.ValidUntil = &until
	p, err := domain.NewDiscountPolicy(params)
	require.NoError(t, err)

	assert.True(t, p.IsActiveOn(time.Date(2026, 6, 1, 0, 0, 0, 0, time.UTC)))
	assert.False(t, p.IsActiveOn(time.Date(2025, 12, 1, 0, 0, 0, 0, time.UTC)))
	assert.False(t, p.IsActiveOn(time.Date(2027, 1, 1, 0, 0, 0, 0, time.UTC)))
}

func TestDiscountPolicy_IsActiveOn_Boundaries(t *testing.T) {
	params := validParams()
	params.ValidFrom = time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	until := time.Date(2026, 12, 31, 0, 0, 0, 0, time.UTC)
	params.ValidUntil = &until
	p, err := domain.NewDiscountPolicy(params)
	require.NoError(t, err)

	// 開始日当日（時刻付き）は有効。
	assert.True(t, p.IsActiveOn(time.Date(2026, 1, 1, 9, 0, 0, 0, time.UTC)))
	// 終了日当日（時刻付き）は有効（DATE 単位比較）。
	assert.True(t, p.IsActiveOn(time.Date(2026, 12, 31, 23, 59, 0, 0, time.UTC)))
	// 開始前日は無効。
	assert.False(t, p.IsActiveOn(time.Date(2025, 12, 31, 23, 59, 0, 0, time.UTC)))
	// 終了翌日は無効。
	assert.False(t, p.IsActiveOn(time.Date(2027, 1, 1, 0, 0, 0, 0, time.UTC)))
}

func TestDiscountPolicy_Update(t *testing.T) {
	p, err := domain.NewDiscountPolicy(validParams())
	require.NoError(t, err)
	err = p.Update(domain.UpdateDiscountPolicyParams{
		PolicyType:  domain.PolicyTypeSeasonal,
		Rate:        0.05,
		ValidFrom:   time.Date(2026, 3, 1, 0, 0, 0, 0, time.UTC),
		ValidUntil:  nil,
		Description: "春季キャンペーン",
	})
	require.NoError(t, err)
	assert.Equal(t, domain.PolicyTypeSeasonal, p.PolicyType())
	assert.InDelta(t, 0.05, p.Rate(), 1e-9)
	assert.Equal(t, "春季キャンペーン", p.Description())
}

func TestPolicyType_Ja(t *testing.T) {
	assert.Equal(t, "法人標準割引", domain.PolicyTypeCorporateStandard.Ja())
	assert.Equal(t, "ボリューム割引", domain.PolicyTypeVolumeDiscount.Ja())
	assert.Equal(t, "シーズン割引", domain.PolicyTypeSeasonal.Ja())
	assert.Equal(t, "割引なし", domain.PolicyTypeNone.Ja())
	assert.Equal(t, "UNKNOWN", domain.PolicyType("UNKNOWN").Ja()) // 未知種別はそのまま返す
}

func TestAllPolicyTypes(t *testing.T) {
	assert.Len(t, domain.AllPolicyTypes(), 4)
}

func TestReconstruct_RestoresAllFields(t *testing.T) {
	until := time.Date(2026, 12, 31, 0, 0, 0, 0, time.UTC)
	from := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	p := domain.Reconstruct(domain.NewDiscountPolicyParams{
		ID:          "POL-9",
		PolicyType:  domain.PolicyTypeVolumeDiscount,
		Rate:        0.20,
		ValidFrom:   from,
		ValidUntil:  &until,
		Description: "数量割引",
	})
	assert.Equal(t, "POL-9", p.ID())
	assert.Equal(t, domain.PolicyTypeVolumeDiscount, p.PolicyType())
	assert.InDelta(t, 0.20, p.Rate(), 1e-9)
	assert.Equal(t, from, p.ValidFrom())
	require.NotNil(t, p.ValidUntil())
	assert.Equal(t, until, *p.ValidUntil())
	assert.Equal(t, "数量割引", p.Description())
}
