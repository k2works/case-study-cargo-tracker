package domain_test

import (
	"testing"
	"time"

	billing "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDiscountRate_Validation(t *testing.T) {
	r, err := billing.NewDiscountRate(0.30)
	require.NoError(t, err)
	assert.InDelta(t, 0.30, r.Value(), 1e-9)

	_, err = billing.NewDiscountRate(0.31)
	assert.ErrorIs(t, err, billing.ErrDiscountRateOutOfRange)
	_, err = billing.NewDiscountRate(-0.01)
	assert.ErrorIs(t, err, billing.ErrDiscountRateOutOfRange)
}

func TestCalculateBaseAmount(t *testing.T) {
	// 距離係数 100（円/kg・スタブ）× 重量 1000kg × 種別係数
	// GENERAL 1.0 → 100000
	assert.Equal(t, int64(100000), billing.CalculateBaseAmount(100, 1000, shared.CargoTypeGeneral).Amount())
	// HAZARDOUS 1.8 → 180000
	assert.Equal(t, int64(180000), billing.CalculateBaseAmount(100, 1000, shared.CargoTypeHazardous).Amount())
	// REFRIGERATED 1.5 → 150000
	assert.Equal(t, int64(150000), billing.CalculateBaseAmount(100, 1000, shared.CargoTypeRefrigerated).Amount())
}

func fixtureInvoiceParams(t *testing.T, corporate bool, rate float64) billing.GenerateInvoiceParams {
	t.Helper()
	id, err := billing.NewInvoiceId("INV-20260720-0001")
	require.NoError(t, err)
	dr, err := billing.NewDiscountRate(rate)
	require.NoError(t, err)
	shipperType := "INDIVIDUAL"
	if corporate {
		shipperType = "CORPORATE"
	}
	return billing.GenerateInvoiceParams{
		InvoiceId:    id,
		BookingId:    billing.NewBillingBookingId("CARGO-001"),
		ShipperId:    billing.NewBillingShipperId("SHP-ABCDEF01", shipperType),
		BaseAmount:   billing.NewMoney(100000, "JPY"),
		DiscountRate: dr,
		TaxRate:      0.10,
		IssuedAt:     time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC),
		DueDays:      30,
	}
}

func TestGenerateInvoice_CorporateDiscountAndTax(t *testing.T) {
	// 法人・割引 10%: 基本 100000 → 割引 10000 → 割引後 90000 → 消費税 9000 → 合計 99000
	inv, err := billing.GenerateInvoice(fixtureInvoiceParams(t, true, 0.10))
	require.NoError(t, err)
	assert.Equal(t, int64(10000), inv.DiscountAmount().Amount())
	assert.Equal(t, int64(9000), inv.TaxAmount().Amount())
	assert.Equal(t, int64(99000), inv.FinalAmount().Amount())
	assert.Equal(t, billing.PaymentStatusPending, inv.PaymentStatus())
	assert.Equal(t, time.Date(2026, 8, 19, 0, 0, 0, 0, time.UTC), inv.DueDate())
}

func TestGenerateInvoice_IndividualNoDiscount(t *testing.T) {
	// 個人・割引率 0: 基本 100000 → 割引 0 → 消費税 10000 → 合計 110000
	inv, err := billing.GenerateInvoice(fixtureInvoiceParams(t, false, 0.0))
	require.NoError(t, err)
	assert.Equal(t, int64(0), inv.DiscountAmount().Amount())
	assert.Equal(t, int64(110000), inv.FinalAmount().Amount())
}

func TestInvoice_ConfirmPayment(t *testing.T) {
	inv, _ := billing.GenerateInvoice(fixtureInvoiceParams(t, true, 0.10))
	paidAt := time.Date(2026, 8, 1, 0, 0, 0, 0, time.UTC)
	require.NoError(t, inv.ConfirmPayment(paidAt))
	assert.Equal(t, billing.PaymentStatusConfirmed, inv.PaymentStatus())
	assert.Equal(t, paidAt, inv.PaidAt())
	// 確定済みの再確定は不可。
	assert.ErrorIs(t, inv.ConfirmPayment(paidAt), billing.ErrInvalidPaymentTransition)
}

func TestInvoice_MarkOverdue(t *testing.T) {
	inv, _ := billing.GenerateInvoice(fixtureInvoiceParams(t, false, 0.0))
	// 期限内は OVERDUE にならない。
	require.NoError(t, inv.MarkOverdue(time.Date(2026, 8, 19, 0, 0, 0, 0, time.UTC)))
	assert.Equal(t, billing.PaymentStatusPending, inv.PaymentStatus())
	// 期限超過で OVERDUE。
	require.NoError(t, inv.MarkOverdue(time.Date(2026, 8, 20, 0, 0, 0, 0, time.UTC)))
	assert.Equal(t, billing.PaymentStatusOverdue, inv.PaymentStatus())
}

func TestInvoice_MarkOverdue_NotAfterConfirmed(t *testing.T) {
	inv, _ := billing.GenerateInvoice(fixtureInvoiceParams(t, false, 0.0))
	require.NoError(t, inv.ConfirmPayment(time.Date(2026, 8, 1, 0, 0, 0, 0, time.UTC)))
	// 確定済みは期限超過でも OVERDUE にしない。
	require.NoError(t, inv.MarkOverdue(time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC)))
	assert.Equal(t, billing.PaymentStatusConfirmed, inv.PaymentStatus())
}

func TestBillingShipperId_IsCorporate(t *testing.T) {
	assert.True(t, billing.NewBillingShipperId("SHP-1", "CORPORATE").IsCorporate())
	assert.False(t, billing.NewBillingShipperId("SHP-1", "INDIVIDUAL").IsCorporate())
}
