//go:build integration

package infrastructure_test

import (
	"context"
	"path/filepath"
	"sort"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/infrastructure"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
)

// allMigrations は全 up マイグレーションを番号順に返す。
func allMigrations(t *testing.T) []string {
	t.Helper()
	dir, err := filepath.Abs("../../../db/migrations")
	require.NoError(t, err)
	files, err := filepath.Glob(filepath.Join(dir, "*.up.sql"))
	require.NoError(t, err)
	sort.Strings(files)
	return files
}

func setupPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	ctx := context.Background()
	container, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("cargo_tracker"),
		postgres.WithUsername("cargo"),
		postgres.WithPassword("cargo"),
		postgres.WithInitScripts(allMigrations(t)...),
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

func loc(t *testing.T, code string) shared.Location {
	t.Helper()
	l, err := shared.NewLocation(code)
	require.NoError(t, err)
	return l
}

func TestHandlingActivityRepository_SaveAndList(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewHandlingActivityRepository(pool)
	ctx := context.Background()

	receive, err := domain.NewHandlingActivity("CARGO-001", domain.HandlingTypeReceive, loc(t, "JPTYO"),
		time.Date(2026, 7, 20, 10, 0, 0, 0, time.UTC), "", "", "作業員A")
	require.NoError(t, err)
	require.NoError(t, repo.Save(ctx, receive))

	load, err := domain.NewHandlingActivity("CARGO-001", domain.HandlingTypeLoad, loc(t, "JPTYO"),
		time.Date(2026, 7, 21, 9, 0, 0, 0, time.UTC), "V001", "", "作業員B")
	require.NoError(t, err)
	require.NoError(t, repo.Save(ctx, load))

	claim, err := domain.NewHandlingActivity("CARGO-001", domain.HandlingTypeClaim, loc(t, "DEHAM"),
		time.Date(2026, 7, 25, 8, 0, 0, 0, time.UTC), "", "署名:山田", "作業員C")
	require.NoError(t, err)
	require.NoError(t, repo.Save(ctx, claim))

	list, err := repo.ListByBookingID(ctx, "CARGO-001")
	require.NoError(t, err)
	require.Len(t, list, 3)
	// event_completion_time 昇順。
	assert.Equal(t, domain.HandlingTypeReceive, list[0].Type())
	assert.Equal(t, domain.HandlingTypeLoad, list[1].Type())
	assert.Equal(t, "V001", list[1].VoyageNumber())
	assert.Equal(t, domain.HandlingTypeClaim, list[2].Type())
	assert.Equal(t, "署名:山田", list[2].ConsigneeConfirmation())

	// 別予約は含まれない。
	empty, err := repo.ListByBookingID(ctx, "CARGO-999")
	require.NoError(t, err)
	assert.Empty(t, empty)
}
