package domain_test

import (
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/domain"
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

func TestNewVoyageNumber(t *testing.T) {
	t.Run("値を持てば生成できる", func(t *testing.T) {
		vn, err := domain.NewVoyageNumber("V0001")
		require.NoError(t, err)
		assert.Equal(t, "V0001", vn.Value())
	})
	t.Run("空はエラー", func(t *testing.T) {
		_, err := domain.NewVoyageNumber("")
		require.ErrorIs(t, err, domain.ErrEmptyVoyageNumber)
	})
}

func TestNewCarrierMovement(t *testing.T) {
	dep := time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC)
	arr := time.Date(2026, 9, 5, 0, 0, 0, 0, time.UTC)

	t.Run("出発≠到着・出発<到着なら生成できる", func(t *testing.T) {
		cm, err := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "USLAX"), dep, arr, 1)
		require.NoError(t, err)
		assert.Equal(t, "JPTYO", cm.Departure().UnLocode())
		assert.Equal(t, 1, cm.Seq())
	})
	t.Run("出発地と到着地が同一はエラー", func(t *testing.T) {
		_, err := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "JPTYO"), dep, arr, 1)
		require.ErrorIs(t, err, domain.ErrSameDepartureArrival)
	})
	t.Run("出発が到着より後はエラー", func(t *testing.T) {
		_, err := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "USLAX"), arr, dep, 1)
		require.ErrorIs(t, err, domain.ErrInvalidMovementDates)
	})
}

func fixtureMovements(t *testing.T) []domain.CarrierMovement {
	t.Helper()
	cm1, _ := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "SGSIN"), time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 5, 0, 0, 0, 0, time.UTC), 1)
	cm2, _ := domain.NewCarrierMovement(mustLoc(t, "SGSIN"), mustLoc(t, "USLAX"), time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 12, 0, 0, 0, 0, time.UTC), 2)
	return []domain.CarrierMovement{cm1, cm2}
}

func TestNewSchedule(t *testing.T) {
	t.Run("時系列順の区間で生成できる", func(t *testing.T) {
		s, err := domain.NewSchedule(fixtureMovements(t))
		require.NoError(t, err)
		assert.Len(t, s.Movements(), 2)
	})
	t.Run("空はエラー", func(t *testing.T) {
		_, err := domain.NewSchedule(nil)
		require.ErrorIs(t, err, domain.ErrEmptySchedule)
	})
	t.Run("区間の連結が破れているとエラー", func(t *testing.T) {
		cm1, _ := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "SGSIN"), time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 5, 0, 0, 0, 0, time.UTC), 1)
		cm2, _ := domain.NewCarrierMovement(mustLoc(t, "DEHAM"), mustLoc(t, "USLAX"), time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 12, 0, 0, 0, 0, time.UTC), 2)
		_, err := domain.NewSchedule([]domain.CarrierMovement{cm1, cm2})
		require.ErrorIs(t, err, domain.ErrDisconnectedSchedule)
	})
}

func TestRegisterVoyage(t *testing.T) {
	vn, _ := domain.NewVoyageNumber("V0001")
	sched, _ := domain.NewSchedule(fixtureMovements(t))
	cts := []shared.CargoType{shared.CargoTypeGeneral, shared.CargoTypeRefrigerated}

	t.Run("航海を登録できる", func(t *testing.T) {
		v, err := domain.RegisterVoyage(vn, "Ever Given", "Evergreen", sched, cts)
		require.NoError(t, err)
		assert.Equal(t, "V0001", v.VoyageNumber().Value())
		assert.Equal(t, "Ever Given", v.VesselName())
		assert.Equal(t, "Evergreen", v.Carrier())
		assert.True(t, v.Supports(shared.CargoTypeRefrigerated))
		assert.False(t, v.Supports(shared.CargoTypeHazardous))
		assert.Equal(t, "JPTYO", v.Origin().UnLocode())
		assert.Equal(t, "USLAX", v.Destination().UnLocode())
	})
	t.Run("船名が空はエラー", func(t *testing.T) {
		_, err := domain.RegisterVoyage(vn, " ", "Evergreen", sched, cts)
		require.ErrorIs(t, err, domain.ErrEmptyVesselName)
	})
	t.Run("運送会社が空はエラー", func(t *testing.T) {
		_, err := domain.RegisterVoyage(vn, "Ever Given", "", sched, cts)
		require.ErrorIs(t, err, domain.ErrEmptyCarrier)
	})
	t.Run("対応貨物種別が空はエラー", func(t *testing.T) {
		_, err := domain.RegisterVoyage(vn, "Ever Given", "Evergreen", sched, nil)
		require.ErrorIs(t, err, domain.ErrEmptyCargoTypes)
	})
}
