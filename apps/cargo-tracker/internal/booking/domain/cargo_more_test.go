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
	shipperCode, _ := shared.NewShipperCode("SHP-1")
	bookingID, _ := domain.NewBookingId("BKG-1")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	deadline := time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC)
	spec, _ := domain.NewRouteSpecification(origin, dest, deadline)
	weight, _ := domain.NewWeight(500)
	temp, _ := domain.NewTemperatureRequirement(-20, -5, domain.TemperatureUnitCelsius)

	cargo, err := domain.RegisterCargo(domain.CargoParams{BookingID: bookingID, ShipperCode: shipperCode, RouteSpec: spec, CargoType: domain.CargoTypeRefrigerated, Weight: weight, BookingAmount: domain.NewMoney(1000, "JPY"), Hazardous: nil, Temperature: &temp})
	require.NoError(t, err)

	assert.Equal(t, "DEHAM", cargo.RouteSpec().Destination().UnLocode())
	assert.Equal(t, deadline, cargo.RouteSpec().ArrivalDeadline())
	assert.Equal(t, int64(1000), cargo.BookingAmount().Amount())
	assert.Equal(t, "JPY", cargo.BookingAmount().Currency())
	assert.Equal(t, domain.CargoTypeRefrigerated, cargo.CargoType())
	require.NotNil(t, cargo.TemperatureRequirement())
	assert.InDelta(t, -20.0, cargo.TemperatureRequirement().MinTemperature(), 0.001)
}
