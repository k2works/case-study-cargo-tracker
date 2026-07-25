package application_test

import (
	"context"
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/application"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func mvt(dep, arr string, depY, depM, depD, arrY, arrM, arrD, seq int) application.MovementInput {
	return application.MovementInput{
		DepartureUnLocode: dep, ArrivalUnLocode: arr,
		DepartureTime: time.Date(depY, time.Month(depM), depD, 0, 0, 0, 0, time.UTC),
		ArrivalTime:   time.Date(arrY, time.Month(arrM), arrD, 0, 0, 0, 0, time.UTC),
		Seq:           seq,
	}
}

func registerVoyage(t *testing.T, repo *stubVoyageRepo, number string, cargoTypes []string, movements ...application.MovementInput) {
	t.Helper()
	svc := application.NewRegisterVoyageService(repo)
	require.NoError(t, svc.Register(context.Background(), application.RegisterVoyageCommand{
		VoyageNumber:        number,
		VesselName:          "Vessel-" + number,
		Carrier:             "Carrier",
		SupportedCargoTypes: cargoTypes,
		Movements:           movements,
	}))
}

func TestSearchRoutesService(t *testing.T) {
	t.Run("直行便と経由便を推奨順で返す", func(t *testing.T) {
		repo := newStubRepo()
		registerVoyage(t, repo, "V-DIRECT", []string{"GENERAL"}, mvt("JPTYO", "USLAX", 2026, 10, 1, 2026, 10, 20, 1))
		registerVoyage(t, repo, "V-LEG1", []string{"GENERAL"}, mvt("JPTYO", "SGSIN", 2026, 10, 1, 2026, 10, 5, 1))
		registerVoyage(t, repo, "V-LEG2", []string{"GENERAL"}, mvt("SGSIN", "USLAX", 2026, 10, 6, 2026, 10, 12, 1))

		svc := application.NewSearchRoutesService(repo)
		got, err := svc.Search(context.Background(), application.RouteSearchQuery{
			OriginUnLocode:      "JPTYO",
			DestinationUnLocode: "USLAX",
			ArrivalDeadline:     time.Date(2026, 12, 1, 0, 0, 0, 0, time.UTC),
			CargoType:           "GENERAL",
		})
		require.NoError(t, err)
		require.Len(t, got, 2)
		assert.Len(t, got[0].Legs, 1) // 直行便が先頭
		assert.Equal(t, "V-DIRECT", got[0].Legs[0].VoyageNumber)
		assert.Len(t, got[1].Legs, 2)
		assert.Equal(t, []string{"SGSIN"}, got[1].Waypoints)
		assert.Positive(t, got[1].EstimatedCost)
	})

	t.Run("該当なしは空を返す", func(t *testing.T) {
		repo := newStubRepo()
		registerVoyage(t, repo, "V-DIRECT", []string{"GENERAL"}, mvt("JPTYO", "USLAX", 2026, 10, 1, 2026, 10, 20, 1))
		svc := application.NewSearchRoutesService(repo)
		got, err := svc.Search(context.Background(), application.RouteSearchQuery{
			OriginUnLocode:      "JPTYO",
			DestinationUnLocode: "USLAX",
			ArrivalDeadline:     time.Date(2026, 10, 10, 0, 0, 0, 0, time.UTC), // 到着より前
			CargoType:           "GENERAL",
		})
		require.NoError(t, err)
		assert.Empty(t, got)
	})

	t.Run("不正な出発地はエラー", func(t *testing.T) {
		repo := newStubRepo()
		svc := application.NewSearchRoutesService(repo)
		_, err := svc.Search(context.Background(), application.RouteSearchQuery{
			OriginUnLocode:      "X",
			DestinationUnLocode: "USLAX",
			CargoType:           "GENERAL",
		})
		require.Error(t, err)
	})

	t.Run("不正な貨物種別はエラー", func(t *testing.T) {
		repo := newStubRepo()
		svc := application.NewSearchRoutesService(repo)
		_, err := svc.Search(context.Background(), application.RouteSearchQuery{
			OriginUnLocode:      "JPTYO",
			DestinationUnLocode: "USLAX",
			CargoType:           "XXX",
		})
		require.Error(t, err)
	})
}
