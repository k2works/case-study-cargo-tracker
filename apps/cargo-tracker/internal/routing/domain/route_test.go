package domain_test

import (
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func loc(t *testing.T, v string) shared.Location {
	t.Helper()
	l, err := shared.NewLocation(v)
	require.NoError(t, err)
	return l
}

func at(y, m, d int) time.Time { return time.Date(y, time.Month(m), d, 0, 0, 0, 0, time.UTC) }

// mustVoyage は指定区間・対応貨物種別で Voyage を生成するテストヘルパ。
func mustVoyage(t *testing.T, number string, cargoTypes []shared.CargoType, movements ...domain.CarrierMovement) *domain.Voyage {
	t.Helper()
	vn, err := domain.NewVoyageNumber(number)
	require.NoError(t, err)
	sch, err := domain.NewSchedule(movements)
	require.NoError(t, err)
	v, err := domain.RegisterVoyage(vn, "Vessel-"+number, "Carrier", sch, cargoTypes)
	require.NoError(t, err)
	return v
}

func mustMovement(t *testing.T, dep, arr shared.Location, depT, arrT time.Time, seq int) domain.CarrierMovement {
	t.Helper()
	m, err := domain.NewCarrierMovement(dep, arr, depT, arrT, seq)
	require.NoError(t, err)
	return m
}

func TestNewRouteSpecification(t *testing.T) {
	t.Run("有効な仕様を生成できる", func(t *testing.T) {
		spec, err := domain.NewRouteSpecification(loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 12, 1), shared.CargoTypeGeneral)
		require.NoError(t, err)
		assert.Equal(t, "JPTYO", spec.Origin().UnLocode())
		assert.Equal(t, "USLAX", spec.Destination().UnLocode())
	})
	t.Run("出発地と目的地が同一はエラー", func(t *testing.T) {
		_, err := domain.NewRouteSpecification(loc(t, "JPTYO"), loc(t, "JPTYO"), at(2026, 12, 1), shared.CargoTypeGeneral)
		require.ErrorIs(t, err, domain.ErrSameSpecOriginDestination)
	})
}

func TestRouteFinder_Direct(t *testing.T) {
	// JPTYO → USLAX 直行便
	direct := mustVoyage(t, "V-DIRECT", []shared.CargoType{shared.CargoTypeGeneral},
		mustMovement(t, loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 10, 1), at(2026, 10, 13), 1),
	)
	spec, _ := domain.NewRouteSpecification(loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 12, 1), shared.CargoTypeGeneral)

	got := domain.RouteFinder{}.FindCandidates(spec, []*domain.Voyage{direct})
	require.Len(t, got, 1)
	assert.Len(t, got[0].Legs(), 1)
	assert.Equal(t, "V-DIRECT", got[0].Legs()[0].VoyageNumber().Value())
	assert.Equal(t, 12, got[0].TransitDays())
	assert.Empty(t, got[0].Waypoints())
}

func TestRouteFinder_Transit_DirectFirst(t *testing.T) {
	// 直行便 V-DIRECT と、経由（JPTYO→SGSIN→USLAX）を提示。直行が先頭。
	direct := mustVoyage(t, "V-DIRECT", []shared.CargoType{shared.CargoTypeGeneral},
		mustMovement(t, loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 10, 1), at(2026, 10, 20), 1),
	)
	leg1 := mustVoyage(t, "V-LEG1", []shared.CargoType{shared.CargoTypeGeneral},
		mustMovement(t, loc(t, "JPTYO"), loc(t, "SGSIN"), at(2026, 10, 1), at(2026, 10, 5), 1),
	)
	leg2 := mustVoyage(t, "V-LEG2", []shared.CargoType{shared.CargoTypeGeneral},
		mustMovement(t, loc(t, "SGSIN"), loc(t, "USLAX"), at(2026, 10, 6), at(2026, 10, 12), 1),
	)
	spec, _ := domain.NewRouteSpecification(loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 12, 1), shared.CargoTypeGeneral)

	got := domain.RouteFinder{}.FindCandidates(spec, []*domain.Voyage{direct, leg1, leg2})
	require.Len(t, got, 2)
	// 直行便（区間数少）が先頭
	assert.Len(t, got[0].Legs(), 1)
	assert.Equal(t, "V-DIRECT", got[0].Legs()[0].VoyageNumber().Value())
	// 経由便は経由港 SGSIN を持つ
	assert.Len(t, got[1].Legs(), 2)
	require.Len(t, got[1].Waypoints(), 1)
	assert.Equal(t, "SGSIN", got[1].Waypoints()[0].UnLocode())
}

func TestRouteFinder_NoConnectionByTime(t *testing.T) {
	// 空間は繋がるが時刻が逆行（次便が前便の到着より前に出発）→ 接続不能
	leg1 := mustVoyage(t, "V-LEG1", []shared.CargoType{shared.CargoTypeGeneral},
		mustMovement(t, loc(t, "JPTYO"), loc(t, "SGSIN"), at(2026, 10, 10), at(2026, 10, 20), 1),
	)
	leg2 := mustVoyage(t, "V-LEG2", []shared.CargoType{shared.CargoTypeGeneral},
		mustMovement(t, loc(t, "SGSIN"), loc(t, "USLAX"), at(2026, 10, 5), at(2026, 10, 12), 1),
	)
	spec, _ := domain.NewRouteSpecification(loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 12, 1), shared.CargoTypeGeneral)

	got := domain.RouteFinder{}.FindCandidates(spec, []*domain.Voyage{leg1, leg2})
	assert.Empty(t, got)
}

func TestRouteFinder_DeadlineExceeded(t *testing.T) {
	direct := mustVoyage(t, "V-DIRECT", []shared.CargoType{shared.CargoTypeGeneral},
		mustMovement(t, loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 10, 1), at(2026, 10, 20), 1),
	)
	// 期限は到着より前 → 候補なし
	spec, _ := domain.NewRouteSpecification(loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 10, 15), shared.CargoTypeGeneral)

	got := domain.RouteFinder{}.FindCandidates(spec, []*domain.Voyage{direct})
	assert.Empty(t, got)
}

func TestRouteFinder_CargoTypeUnsupported(t *testing.T) {
	// 冷蔵貨物を要求するが直行便は一般のみ対応 → 候補なし
	direct := mustVoyage(t, "V-DIRECT", []shared.CargoType{shared.CargoTypeGeneral},
		mustMovement(t, loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 10, 1), at(2026, 10, 13), 1),
	)
	spec, _ := domain.NewRouteSpecification(loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 12, 1), shared.CargoTypeRefrigerated)

	got := domain.RouteFinder{}.FindCandidates(spec, []*domain.Voyage{direct})
	assert.Empty(t, got)
}

func TestRouteCandidate_Getters(t *testing.T) {
	direct := mustVoyage(t, "V-DIRECT", []shared.CargoType{shared.CargoTypeGeneral},
		mustMovement(t, loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 10, 1), at(2026, 10, 13), 1),
	)
	spec, _ := domain.NewRouteSpecification(loc(t, "JPTYO"), loc(t, "USLAX"), at(2026, 12, 1), shared.CargoTypeGeneral)
	got := domain.RouteFinder{}.FindCandidates(spec, []*domain.Voyage{direct})
	require.Len(t, got, 1)
	c := got[0]
	assert.Equal(t, "JPTYO", c.Origin().UnLocode())
	assert.Equal(t, "USLAX", c.Destination().UnLocode())
	assert.Equal(t, at(2026, 10, 1), c.DepartureTime())
	assert.Equal(t, at(2026, 10, 13), c.ArrivalTime())
	assert.Positive(t, c.EstimatedCost())
	leg := c.Legs()[0]
	assert.Equal(t, "JPTYO", leg.Load().UnLocode())
	assert.Equal(t, "USLAX", leg.Unload().UnLocode())
	assert.Equal(t, at(2026, 10, 1), leg.LoadTime())
	assert.Equal(t, at(2026, 10, 13), leg.UnloadTime())
}
