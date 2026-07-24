package domain_test

import (
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestBookingIdEmpty(t *testing.T) {
	_, err := domain.NewBookingId("")
	require.ErrorIs(t, err, domain.ErrEmptyBookingId)
}

func TestCargoGetters(t *testing.T) {
	shipperID, _ := shared.NewShipperId("SHP-1")
	bookingID, _ := domain.NewBookingId("BKG-1")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	deadline := time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC)
	spec, _ := domain.NewRouteSpecification(origin, dest, deadline)
	weight, _ := domain.NewWeight(500)

	cargo, err := domain.RegisterCargo(bookingID, shipperID, spec, domain.CargoTypeRefrigerated, weight, domain.NewMoney(1000, "JPY"))
	require.NoError(t, err)

	assert.Equal(t, "DEHAM", cargo.RouteSpec().Destination().UnLocode())
	assert.Equal(t, deadline, cargo.RouteSpec().ArrivalDeadline())
	assert.Equal(t, int64(1000), cargo.BookingAmount().Amount())
	assert.Equal(t, "JPY", cargo.BookingAmount().Currency())
	assert.Equal(t, domain.CargoTypeRefrigerated, cargo.CargoType())
}
