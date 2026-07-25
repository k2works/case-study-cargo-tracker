//go:build integration

package infrastructure_test

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/infrastructure"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
)

func setupPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	ctx := context.Background()
	migrations, err := filepath.Abs("../../../db/migrations")
	require.NoError(t, err)

	container, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("cargo_tracker"),
		postgres.WithUsername("cargo"),
		postgres.WithPassword("cargo"),
		postgres.WithInitScripts(
			filepath.Join(migrations, "000007_create_estimate.up.sql"),
			filepath.Join(migrations, "000010_add_route_candidate_waypoints.up.sql"),
		),
		postgres.BasicWaitStrategies(),
	)
	require.NoError(t, err)
	t.Cleanup(func() { _ = container.Terminate(ctx) })

	dsn, err := container.ConnectionString(ctx, "sslmode=disable")
	require.NoError(t, err)
	pool, err := pgxpool.New(ctx, dsn)
	require.NoError(t, err)
	t.Cleanup(pool.Close)
	return pool
}

func TestEstimateRepository_SaveAndFind(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewEstimateRepository(pool)
	ctx := context.Background()

	id, _ := domain.NewEstimateId("11111111-2222-3333-4444-555555555555")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("USLAX")
	rc1, _ := domain.NewRouteCandidate("V-DIRECT", 12, 200000, nil)
	rc2, _ := domain.NewRouteCandidate("V-TRANSIT", 18, 150000, nil)
	e, err := domain.CreateEstimate(id, origin, dest, time.Date(2026, 10, 1, 0, 0, 0, 0, time.UTC), shared.CargoTypeGeneral, 1200.5, []domain.RouteCandidate{rc1, rc2}, time.Date(2026, 8, 1, 0, 0, 0, 0, time.UTC))
	require.NoError(t, err)
	require.NoError(t, repo.Save(ctx, e))

	got, err := repo.FindByEstimateId(ctx, id)
	require.NoError(t, err)
	assert.Equal(t, "JPTYO", got.Origin().UnLocode())
	assert.Equal(t, "USLAX", got.Destination().UnLocode())
	assert.Equal(t, domain.EstimateStatusCreated, got.Status())
	assert.InDelta(t, 1200.5, got.WeightKg(), 0.001)
	require.Len(t, got.Candidates(), 2)
	assert.Equal(t, "V-DIRECT", got.Candidates()[0].VoyageNumber())
	assert.EqualValues(t, 200000, got.Candidates()[0].EstimatedCost())

	all, err := repo.ListAll(ctx)
	require.NoError(t, err)
	assert.Len(t, all, 1)
}

func TestEstimateRepository_NotFound(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewEstimateRepository(pool)
	id, _ := domain.NewEstimateId("99999999-8888-7777-6666-555555555555")
	_, err := repo.FindByEstimateId(context.Background(), id)
	require.ErrorIs(t, err, infrastructure.ErrEstimateNotFound)
}
