//go:build integration

package infrastructure_test

import (
	"context"
	"path/filepath"
	"sort"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/infrastructure"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
)

func setupPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	ctx := context.Background()
	dir, err := filepath.Abs("../../../db/migrations")
	require.NoError(t, err)
	files, err := filepath.Glob(filepath.Join(dir, "*.up.sql"))
	require.NoError(t, err)
	sort.Strings(files)
	container, err := postgres.Run(ctx, "postgres:16-alpine",
		postgres.WithDatabase("cargo_tracker"), postgres.WithUsername("cargo"), postgres.WithPassword("cargo"),
		postgres.WithInitScripts(files...), postgres.BasicWaitStrategies())
	require.NoError(t, err)
	t.Cleanup(func() { _ = container.Terminate(ctx) })
	dsn, err := container.ConnectionString(ctx, "sslmode=disable")
	require.NoError(t, err)
	pool, err := pgxpool.New(ctx, dsn)
	require.NoError(t, err)
	t.Cleanup(pool.Close)
	return pool
}

func newPolicy(t *testing.T, id string, until *time.Time) *domain.DiscountPolicy {
	t.Helper()
	p, err := domain.NewDiscountPolicy(domain.NewDiscountPolicyParams{
		ID:          id,
		PolicyType:  domain.PolicyTypeCorporateStandard,
		Rate:        0.15,
		ValidFrom:   time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC),
		ValidUntil:  until,
		Description: "法人標準割引",
	})
	require.NoError(t, err)
	return p
}

func TestRepository_SaveFindList(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewDiscountPolicyRepository(pool)
	ctx := context.Background()
	id := "11111111-1111-1111-1111-111111111111"

	require.NoError(t, repo.Save(ctx, newPolicy(t, id, nil)))

	got, err := repo.FindByID(ctx, id)
	require.NoError(t, err)
	assert.InDelta(t, 0.15, got.Rate(), 1e-6)
	assert.Equal(t, domain.PolicyTypeCorporateStandard, got.PolicyType())
	assert.Nil(t, got.ValidUntil())

	list, err := repo.List(ctx)
	require.NoError(t, err)
	require.Len(t, list, 1)
	assert.Equal(t, id, list[0].ID())
}

func TestRepository_Upsert(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewDiscountPolicyRepository(pool)
	ctx := context.Background()
	id := "22222222-2222-2222-2222-222222222222"
	require.NoError(t, repo.Save(ctx, newPolicy(t, id, nil)))

	p, err := repo.FindByID(ctx, id)
	require.NoError(t, err)
	require.NoError(t, p.Update(domain.UpdateDiscountPolicyParams{
		PolicyType: domain.PolicyTypeSeasonal, Rate: 0.05,
		ValidFrom: time.Date(2026, 3, 1, 0, 0, 0, 0, time.UTC),
	}))
	require.NoError(t, repo.Save(ctx, p))

	updated, err := repo.FindByID(ctx, id)
	require.NoError(t, err)
	assert.Equal(t, domain.PolicyTypeSeasonal, updated.PolicyType())
	assert.InDelta(t, 0.05, updated.Rate(), 1e-6)

	list, err := repo.List(ctx)
	require.NoError(t, err)
	assert.Len(t, list, 1) // upsert なので重複しない
}

func TestRepository_NotFound(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewDiscountPolicyRepository(pool)
	_, err := repo.FindByID(context.Background(), "33333333-3333-3333-3333-333333333333")
	assert.ErrorIs(t, err, application.ErrPolicyNotFound)
}
