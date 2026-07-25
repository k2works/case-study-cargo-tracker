package domain_test

import (
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/domain"
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

func TestNewRouteCandidate(t *testing.T) {
	t.Run("有効な値で生成できる", func(t *testing.T) {
		rc, err := domain.NewRouteCandidate("V0001", 12, 150000)
		require.NoError(t, err)
		assert.Equal(t, "V0001", rc.VoyageNumber())
		assert.Equal(t, 12, rc.TransitDays())
		assert.EqualValues(t, 150000, rc.EstimatedCost())
	})
	t.Run("航海番号が空はエラー", func(t *testing.T) {
		_, err := domain.NewRouteCandidate("", 12, 150000)
		require.ErrorIs(t, err, domain.ErrEmptyVoyageNumber)
	})
	t.Run("所要日数が0以下はエラー", func(t *testing.T) {
		_, err := domain.NewRouteCandidate("V0001", 0, 150000)
		require.ErrorIs(t, err, domain.ErrNonPositiveTransitDays)
	})
	t.Run("コストが0以下はエラー", func(t *testing.T) {
		_, err := domain.NewRouteCandidate("V0001", 12, 0)
		require.ErrorIs(t, err, domain.ErrNonPositiveCost)
	})
}

func TestCreateEstimate(t *testing.T) {
	id, _ := domain.NewEstimateId("11111111-2222-3333-4444-555555555555")
	deadline := time.Date(2026, 10, 1, 0, 0, 0, 0, time.UTC)
	rc, _ := domain.NewRouteCandidate("V0001", 12, 150000)

	t.Run("見積を作成できる", func(t *testing.T) {
		e, err := domain.CreateEstimate(id, mustLoc(t, "JPTYO"), mustLoc(t, "USLAX"), deadline, shared.CargoTypeGeneral, 1200.5, []domain.RouteCandidate{rc})
		require.NoError(t, err)
		assert.Equal(t, "JPTYO", e.Origin().UnLocode())
		assert.Equal(t, domain.EstimateStatusCreated, e.Status())
		assert.Len(t, e.Candidates(), 1)
	})
	t.Run("出発地と目的地が同一はエラー", func(t *testing.T) {
		_, err := domain.CreateEstimate(id, mustLoc(t, "JPTYO"), mustLoc(t, "JPTYO"), deadline, shared.CargoTypeGeneral, 1200.5, []domain.RouteCandidate{rc})
		require.ErrorIs(t, err, domain.ErrSameOriginDestination)
	})
	t.Run("重量が0以下はエラー", func(t *testing.T) {
		_, err := domain.CreateEstimate(id, mustLoc(t, "JPTYO"), mustLoc(t, "USLAX"), deadline, shared.CargoTypeGeneral, 0, []domain.RouteCandidate{rc})
		require.ErrorIs(t, err, domain.ErrNonPositiveWeight)
	})
}
