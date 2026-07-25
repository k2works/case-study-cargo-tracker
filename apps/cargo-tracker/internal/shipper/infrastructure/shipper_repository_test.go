//go:build integration

package infrastructure_test

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/infrastructure"
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
			filepath.Join(migrations, "000001_create_shipper.up.sql"),
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

func mustCorporate(t *testing.T) *domain.Shipper {
	t.Helper()
	id, _ := domain.NewShipperId("11111111-2222-3333-4444-555555555555")
	name, _ := domain.NewShipperName("株式会社サンプル")
	email, _ := domain.NewEmail("corp@example.com")
	cn, _ := domain.NewContractNumber("CN-0001")
	rate, _ := domain.NewDiscountRate(0.10)
	s, err := domain.RegisterCorporateShipper(id, domain.GenerateShipperCode("abcdef12-3456-7890-abcd-ef1234567890"), name, email, cn, rate)
	require.NoError(t, err)
	return s
}

func TestShipperRepository_SaveAndExists(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewShipperRepository(pool)
	ctx := context.Background()

	t.Run("法人荷主を保存できメール存在確認が真になる", func(t *testing.T) {
		shipper := mustCorporate(t)

		err := repo.Save(ctx, shipper)
		require.NoError(t, err)

		exists, err := repo.ExistsByEmail(ctx, shipper.Email())
		require.NoError(t, err)
		assert.True(t, exists)
	})

	t.Run("未登録メールは存在確認が偽になる", func(t *testing.T) {
		other, _ := domain.NewEmail("nobody@example.com")
		exists, err := repo.ExistsByEmail(ctx, other)
		require.NoError(t, err)
		assert.False(t, exists)
	})
}
