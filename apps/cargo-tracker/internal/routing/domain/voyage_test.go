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
	t.Run("時刻が逆行しているとエラー（次区間の出発が前区間の到着より前）", func(t *testing.T) {
		cm1, _ := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "SGSIN"), time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 12, 0, 0, 0, 0, time.UTC), 1)
		cm2, _ := domain.NewCarrierMovement(mustLoc(t, "SGSIN"), mustLoc(t, "USLAX"), time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 5, 0, 0, 0, 0, time.UTC), 2)
		_, err := domain.NewSchedule([]domain.CarrierMovement{cm1, cm2})
		require.ErrorIs(t, err, domain.ErrOutOfOrderSchedule)
	})
	t.Run("3区間の連結破れも検出する", func(t *testing.T) {
		cm1, _ := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "SGSIN"), time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 5, 0, 0, 0, 0, time.UTC), 1)
		cm2, _ := domain.NewCarrierMovement(mustLoc(t, "SGSIN"), mustLoc(t, "AEJEA"), time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 9, 0, 0, 0, 0, time.UTC), 2)
		cm3, _ := domain.NewCarrierMovement(mustLoc(t, "DEHAM"), mustLoc(t, "USLAX"), time.Date(2026, 9, 10, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 15, 0, 0, 0, 0, time.UTC), 3)
		_, err := domain.NewSchedule([]domain.CarrierMovement{cm1, cm2, cm3})
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

func TestVoyageRestoreAndGetters(t *testing.T) {
	vn, _ := domain.NewVoyageNumber("V0009")
	movs := fixtureMovements(t)
	sched, _ := domain.NewSchedule(movs)
	cts := []shared.CargoType{shared.CargoTypeGeneral}

	v := domain.Restore(vn, "Ever Ace", "Evergreen", sched, cts)
	assert.Equal(t, "V0009", v.VoyageNumber().Value())
	assert.Equal(t, "Ever Ace", v.VesselName())
	assert.Len(t, v.Schedule().Movements(), 2)
	assert.Len(t, v.SupportedCargoTypes(), 1)
	// CarrierMovement の時刻ゲッター
	first := v.Schedule().Movements()[0]
	assert.False(t, first.DepartureTime().IsZero())
	assert.True(t, first.DepartureTime().Before(first.ArrivalTime()))
}

func TestVoyageUpdateSchedule(t *testing.T) {
	vn, _ := domain.NewVoyageNumber("V0010")
	sched, _ := domain.NewSchedule(fixtureMovements(t))
	v, _ := domain.RegisterVoyage(vn, "Old", "OldCarrier", sched, []shared.CargoType{shared.CargoTypeGeneral})

	cm, _ := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "USLAX"), time.Date(2026, 10, 1, 0, 0, 0, 0, time.UTC), time.Date(2026, 10, 10, 0, 0, 0, 0, time.UTC), 1)
	newSched, _ := domain.NewSchedule([]domain.CarrierMovement{cm})

	t.Run("スケジュール・属性を更新できる", func(t *testing.T) {
		require.NoError(t, v.UpdateSchedule("New", "NewCarrier", newSched, []shared.CargoType{shared.CargoTypeRefrigerated}))
		assert.Equal(t, "New", v.VesselName())
		assert.Equal(t, "NewCarrier", v.Carrier())
		assert.Len(t, v.Schedule().Movements(), 1)
		assert.True(t, v.Supports(shared.CargoTypeRefrigerated))
	})
	t.Run("船名空はエラー", func(t *testing.T) {
		require.ErrorIs(t, v.UpdateSchedule("", "C", newSched, []shared.CargoType{shared.CargoTypeGeneral}), domain.ErrEmptyVesselName)
	})
	t.Run("運送会社空はエラー", func(t *testing.T) {
		require.ErrorIs(t, v.UpdateSchedule("V", "", newSched, []shared.CargoType{shared.CargoTypeGeneral}), domain.ErrEmptyCarrier)
	})
	t.Run("対応貨物種別空はエラー", func(t *testing.T) {
		require.ErrorIs(t, v.UpdateSchedule("V", "C", newSched, nil), domain.ErrEmptyCargoTypes)
	})
}
