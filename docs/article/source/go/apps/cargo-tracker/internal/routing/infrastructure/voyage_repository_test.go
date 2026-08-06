//go:build integration

package infrastructure_test

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/infrastructure"
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
			filepath.Join(migrations, "000006_create_voyage.up.sql"),
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

func mustLoc(t *testing.T, v string) shared.Location {
	t.Helper()
	l, err := shared.NewLocation(v)
	require.NoError(t, err)
	return l
}

func fixtureVoyage(t *testing.T, number string) *domain.Voyage {
	t.Helper()
	vn, _ := domain.NewVoyageNumber(number)
	cm1, _ := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "SGSIN"), time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 5, 0, 0, 0, 0, time.UTC), 1)
	cm2, _ := domain.NewCarrierMovement(mustLoc(t, "SGSIN"), mustLoc(t, "USLAX"), time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC), time.Date(2026, 9, 12, 0, 0, 0, 0, time.UTC), 2)
	sched, _ := domain.NewSchedule([]domain.CarrierMovement{cm1, cm2})
	v, err := domain.RegisterVoyage(vn, "Ever Given", "Evergreen", sched, []shared.CargoType{shared.CargoTypeGeneral, shared.CargoTypeRefrigerated})
	require.NoError(t, err)
	return v
}

func TestVoyageRepository_SaveAndFind(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewVoyageRepository(pool)
	ctx := context.Background()

	vn, _ := domain.NewVoyageNumber("V0001")
	require.NoError(t, repo.Save(ctx, fixtureVoyage(t, "V0001")))

	exists, err := repo.Exists(ctx, vn)
	require.NoError(t, err)
	assert.True(t, exists)

	got, err := repo.FindByNumber(ctx, vn)
	require.NoError(t, err)
	assert.Equal(t, "Ever Given", got.VesselName())
	assert.Equal(t, "Evergreen", got.Carrier())
	assert.Equal(t, "JPTYO", got.Origin().UnLocode())
	assert.Equal(t, "USLAX", got.Destination().UnLocode())
	assert.Len(t, got.Schedule().Movements(), 2)
	assert.True(t, got.Supports(shared.CargoTypeRefrigerated))
	assert.False(t, got.Supports(shared.CargoTypeHazardous))
}

func TestVoyageRepository_Update(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewVoyageRepository(pool)
	ctx := context.Background()

	require.NoError(t, repo.Save(ctx, fixtureVoyage(t, "V0002")))
	vn, _ := domain.NewVoyageNumber("V0002")

	got, err := repo.FindByNumber(ctx, vn)
	require.NoError(t, err)
	cm, _ := domain.NewCarrierMovement(mustLoc(t, "JPTYO"), mustLoc(t, "USLAX"), time.Date(2026, 10, 1, 0, 0, 0, 0, time.UTC), time.Date(2026, 10, 10, 0, 0, 0, 0, time.UTC), 1)
	sched, _ := domain.NewSchedule([]domain.CarrierMovement{cm})
	require.NoError(t, got.UpdateSchedule("Ever Ace", "Evergreen", sched, []shared.CargoType{shared.CargoTypeGeneral}))
	require.NoError(t, repo.Update(ctx, got))

	updated, err := repo.FindByNumber(ctx, vn)
	require.NoError(t, err)
	assert.Equal(t, "Ever Ace", updated.VesselName())
	assert.Len(t, updated.Schedule().Movements(), 1)
	assert.False(t, updated.Supports(shared.CargoTypeRefrigerated))
}

func TestVoyageRepository_NotFound(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewVoyageRepository(pool)
	vn, _ := domain.NewVoyageNumber("V-NONE")
	_, err := repo.FindByNumber(context.Background(), vn)
	require.ErrorIs(t, err, infrastructure.ErrVoyageNotFound)
}
