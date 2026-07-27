package domain_test

import (
	"testing"

	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func confirmedCargo(t *testing.T) *domain.Cargo {
	t.Helper()
	bid, err := domain.NewBookingId("CARGO-001")
	require.NoError(t, err)
	sc, err := shared.NewShipperCode("SHP-ABCDEF01")
	require.NoError(t, err)
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, err := domain.NewRouteSpecification(origin, dest, time.Time{})
	require.NoError(t, err)
	weight, _ := domain.NewWeight(1000)
	cargo, err := domain.RegisterCargo(domain.CargoParams{
		BookingID:     bid,
		ShipperCode:   sc,
		RouteSpec:     spec,
		CargoType:     domain.CargoTypeGeneral,
		Weight:        weight,
		BookingAmount: domain.NewMoney(1000, "JPY"),
	})
	require.NoError(t, err)
	require.NoError(t, cargo.Confirm())
	return cargo
}

func TestCargo_IssueTrackingNumber(t *testing.T) {
	cargo := confirmedCargo(t)
	assert.True(t, cargo.CanIssueTrackingNumber())

	err := cargo.IssueTrackingNumber("TRK-20260720-0001")
	require.NoError(t, err)

	assert.Equal(t, domain.BookingStatusTrackingIssued, cargo.Status())
	assert.Equal(t, "TRK-20260720-0001", cargo.TrackingNumber())
	assert.Equal(t, shared.TransportStatusNotReceived, cargo.TransportStatus())
	// 発行済みからは再発行できない。
	assert.False(t, cargo.CanIssueTrackingNumber())
}

func TestCargo_IssueTrackingNumber_RequiresConfirmed(t *testing.T) {
	bid, _ := domain.NewBookingId("CARGO-002")
	sc, _ := shared.NewShipperCode("SHP-ABCDEF01")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, _ := domain.NewRouteSpecification(origin, dest, time.Time{})
	weight, _ := domain.NewWeight(1000)
	cargo, err := domain.RegisterCargo(domain.CargoParams{
		BookingID: bid, ShipperCode: sc, RouteSpec: spec,
		CargoType: domain.CargoTypeGeneral, Weight: weight, BookingAmount: domain.NewMoney(1000, "JPY"),
	})
	require.NoError(t, err)

	// PRELIMINARY 状態では発行不可。
	assert.False(t, cargo.CanIssueTrackingNumber())
	err = cargo.IssueTrackingNumber("TRK-20260720-0001")
	assert.ErrorIs(t, err, domain.ErrInvalidStatusTransition)
}
