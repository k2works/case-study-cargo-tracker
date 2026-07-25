package domain_test

import (
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func mustLoc(t *testing.T, v string) shared.Location {
	t.Helper()
	l, err := shared.NewLocation(v)
	require.NoError(t, err)
	return l
}

func TestParseCargoType(t *testing.T) {
	for _, v := range []string{"GENERAL", "HAZARDOUS", "REFRIGERATED"} {
		t.Run(v+" を解釈できる", func(t *testing.T) {
			ct, err := domain.ParseCargoType(v)
			require.NoError(t, err)
			assert.Equal(t, domain.CargoType(v), ct)
		})
	}
	t.Run("未知の種別はエラー", func(t *testing.T) {
		_, err := domain.ParseCargoType("XXX")
		require.Error(t, err)
	})
}

func TestNewRouteSpecification(t *testing.T) {
	t.Run("出発地と目的地が異なれば生成できる", func(t *testing.T) {
		spec, err := domain.NewRouteSpecification(mustLoc(t, "JPTYO"), mustLoc(t, "DEHAM"), time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC))
		require.NoError(t, err)
		assert.Equal(t, "JPTYO", spec.Origin().UnLocode())
	})

	t.Run("出発地と目的地が同一だとエラー", func(t *testing.T) {
		_, err := domain.NewRouteSpecification(mustLoc(t, "JPTYO"), mustLoc(t, "JPTYO"), time.Now())
		require.ErrorIs(t, err, domain.ErrSameOriginDestination)
	})
}

func TestRegisterCargo(t *testing.T) {
	shipperCode, _ := shared.NewShipperCode("SHP-00000001")
	bookingID, _ := domain.NewBookingId("BKG-0001")
	spec, _ := domain.NewRouteSpecification(mustLoc(t, "JPTYO"), mustLoc(t, "DEHAM"), time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC))

	t.Run("貨物予約を登録すると状態が PRELIMINARY になる", func(t *testing.T) {
		weight, err := domain.NewWeight(1200.5)
		require.NoError(t, err)

		cargo, err := domain.RegisterCargo(domain.CargoParams{BookingID: bookingID, ShipperCode: shipperCode, RouteSpec: spec, CargoType: domain.CargoTypeGeneral, Weight: weight, BookingAmount: domain.NewMoney(0, "JPY"), Hazardous: nil, Temperature: nil})
		require.NoError(t, err)

		assert.Equal(t, domain.BookingStatusPreliminary, cargo.Status())
		assert.Equal(t, "BKG-0001", cargo.BookingID().Value())
		assert.Equal(t, "SHP-00000001", cargo.ShipperCode().Value())
		assert.Equal(t, domain.CargoTypeGeneral, cargo.CargoType())
		assert.InDelta(t, 1200.5, cargo.Weight().Kg(), 0.001)
	})

	t.Run("重量が 0 以下はエラー", func(t *testing.T) {
		_, err := domain.NewWeight(0)
		require.Error(t, err)
	})
}
