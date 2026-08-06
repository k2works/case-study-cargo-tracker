package domain_test

import (
	"testing"
	"time"

	billing "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDiscountPolicy_CalculateRate(t *testing.T) {
	policy := billing.DiscountPolicy{}
	contract, _ := billing.NewDiscountRate(0.20)

	// 法人は契約割引率を適用。
	corporate := billing.NewBillingShipperId("SHP-1", "CORPORATE")
	assert.InDelta(t, 0.20, policy.CalculateRate(corporate, contract).Value(), 1e-9)

	// 個人は割引なし。
	individual := billing.NewBillingShipperId("SHP-2", "INDIVIDUAL")
	assert.InDelta(t, 0.0, policy.CalculateRate(individual, contract).Value(), 1e-9)
}

func TestInvoice_Accessors(t *testing.T) {
	id, _ := billing.NewInvoiceId("INV-20260720-0001")
	dr, _ := billing.NewDiscountRate(0.10)
	inv, err := billing.GenerateInvoice(billing.GenerateInvoiceParams{
		InvoiceId:    id,
		BookingId:    billing.NewBillingBookingId("CARGO-001"),
		ShipperId:    billing.NewBillingShipperId("SHP-ABCDEF01", "CORPORATE"),
		BaseAmount:   billing.NewMoney(100000, "JPY"),
		DiscountRate: dr,
		TaxRate:      0.10,
		IssuedAt:     time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC),
		DueDays:      30,
	})
	require.NoError(t, err)
	assert.Equal(t, "INV-20260720-0001", inv.InvoiceId().Value())
	assert.Equal(t, "CARGO-001", inv.BookingId().Value())
	assert.Equal(t, "SHP-ABCDEF01", inv.ShipperId().ShipperCode())
	assert.Equal(t, int64(100000), inv.BaseAmount().Amount())
	assert.InDelta(t, 0.10, inv.DiscountRate().Value(), 1e-9)
	assert.Equal(t, time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC), inv.IssuedAt())
	assert.True(t, inv.PaidAt().IsZero())
}

func TestPaymentStatus_Ja(t *testing.T) {
	assert.Equal(t, "未払い", billing.PaymentStatusPending.Ja())
	assert.Equal(t, "精算済み", billing.PaymentStatusConfirmed.Ja())
	assert.Equal(t, "期限超過", billing.PaymentStatusOverdue.Ja())
	assert.Equal(t, "返金済み", billing.PaymentStatusRefunded.Ja())
	assert.Equal(t, "X", billing.PaymentStatus("X").Ja())
}

func TestReconstructInvoice(t *testing.T) {
	id, _ := billing.NewInvoiceId("INV-20260720-0002")
	dr, _ := billing.NewDiscountRate(0.0)
	p := billing.GenerateInvoiceParams{
		InvoiceId: id, BookingId: billing.NewBillingBookingId("CARGO-002"),
		ShipperId:  billing.NewBillingShipperId("SHP-2", "INDIVIDUAL"),
		BaseAmount: billing.NewMoney(50000, "JPY"), DiscountRate: dr, TaxRate: 0.10,
		IssuedAt: time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC), DueDays: 30,
	}
	inv := billing.ReconstructInvoice(p, billing.NewMoney(0, "JPY"), billing.NewMoney(5000, "JPY"),
		billing.NewMoney(55000, "JPY"), billing.PaymentStatusConfirmed, time.Date(2026, 8, 1, 0, 0, 0, 0, time.UTC))
	assert.Equal(t, billing.PaymentStatusConfirmed, inv.PaymentStatus())
	assert.Equal(t, int64(55000), inv.FinalAmount().Amount())
}
